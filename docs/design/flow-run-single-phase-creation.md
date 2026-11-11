# Single-phase flow-run creation

Status: implemented.

## Problem

A sub-flow trigger currently creates an empty `CREATED` flow-run row only to reserve an id for
the parent job, then a Quartz one-off (`JobFlowRunner`) rebuilds the full candidate and overwrites
the row. The reservation is the only producer of flow-run `CREATED`. It adds a lifecycle state
that is not a business state, a stuck-row failure mode when the trigger or definition disappears,
and an extra scheduling hop with no domain benefit.

## Design

A flow run is created once, in one transaction, with its full snapshot and its initial status
(`WAITING` or `SUBMITTED`). Flow-run `CREATED` is no longer produced.

- `FlowRunCreationService` owns creation end to end: it builds the run snapshot from a `JobFlow`
  (definition fields, merged execution config, dependency-derived initial status, owning host),
  locks the flow definition row, fails expired `WAITING` runs, evaluates conflicts against the
  active-run snapshot, and inserts the run. It is the only component that creates a flow run.
- Adapters only translate triggers into `FlowRunTriggerService#handle`, which delegates creation
  and then publishes the outcome:
  - `JobFlowRunner` handles cron and manual Quartz triggers: load the flow by code, call `handle`.
  - `FlowCommandExecutor` handles sub-flows: load the flow by id, call `handle`, and return the
    child id and status in its `JobCallback`.
- `JOB_LIST` keeps its distinct create/block path in `LegacyJobListService` for both adapters.
  Once created, its runs use the shared flow-run lifecycle.

### Contract

```java
FlowRunCreationResult tryCreateFlowRun(
        JobFlow jobFlow, @Nullable ExecutionConfig configOverride, @Nullable Long triggerUserId, LocalDateTime scheduleTime);
```

The service returns `CREATED` (run saved, id populated) or `BLOCKED` (no save: an admission
conflict, or the legacy `JOB_LIST` start-job check), plus any expired-`WAITING` timeouts. Adapters
call `FlowRunTriggerService#handle`, which delegates to this method and then publishes every
timeout, block, and creation outcome, so alerting lives in one place.

For sub-flows the parent callback now carries `WAITING` or `SUBMITTED` instead of `CREATED`.
`FlowCommandExecutor` fails the parent job when the sub-flow definition is not runnable or when
admission is blocked, instead of leaving a reservation behind.

## State model impact

- Flow-run `CREATED` has no producer. Existing rows are legacy: a kill maps them directly to
  `KILLED`. `ExecutionStatus.CREATED` remains in the enum for the job-run lifecycle.
- Dispatch is unchanged: `SUBMITTED` runs are claimed by `FlowRunCoordinator`; `WAITING` runs are
  advanced by dependency evaluation.

## Rollout and compatibility

- Deploy with a short drain of sub-flow triggers (pause `SUB_FLOW` execution or let pending
  Quartz triggers fire) so no legacy reservation is in flight. No compatibility flag or shim.
- **Breaking:** a flow run left in `CREATED` by the old reservation is still counted as active
  (`JobFlowRunService.listUnfinishedRuns` includes every non-terminal status), but is never dispatched
  (the dispatcher claims only runs owned by the local host) and never expired (expiry only touches
  `WAITING`). With the default `maxActiveRuns = 1`, every later trigger for that flow stays
  `BLOCKED` until the row is changed.
- Before cutover, inspect and reconcile residual rows:

  ```sql
  SELECT id, flow_id, host, create_time FROM t_job_flow_run WHERE status = 'CREATED';
  ```

  Rows reserved by the old sub-flow path have an empty `host`. Set them to `FAILURE`, or `KILLED`
  when a parent job still tracks them, and re-run the query until it returns nothing.

## Follow-up: reject invalid sub-flow references up front

`FlowCommandExecutor` fails the parent job at run time when the referenced definition is missing or
not `ONLINE`/`SCHEDULING`. Rejecting the same mistake when the user edits or starts a flow is
better than surfacing it on the next run. Cases to implement and assert:

| # | Case | Expected |
|---|------|----------|
| 1 | DAG references a flow id that does not exist | reject on save and on start |
| 2 | Referenced flow status is `OFFLINE` or `DELETE` | reject on save and on start |
| 3 | Referenced flow belongs to another workspace | reject, or document cross-workspace sharing explicitly |
| 4 | Flow references itself | reject on save |
| 5 | Reference cycle (`A -> B -> A`) | reject on save |
| 6 | `schedule/start` or `schedule/runOnce` on a flow whose DAG has an invalid sub-flow | reject before scheduling |
| 7 | Referenced flow is taken offline while an `ONLINE`/`SCHEDULING` parent references it | existing guard (`JobInfoMapper#queryRunnableJobUsingJobFlow`) rejects the offline update |
| 8 | Referenced flow goes offline while the parent is `OFFLINE`, then the parent goes online or is scheduled | case 6 must catch it; the existing guard does not re-run |
| 9 | `purge` a flow referenced by any parent DAG | reject on purge |
| 10 | Referenced flow type is `JOB_LIST` (created `ONLINE`) | allowed today; confirm intentionally |

Keep the run-time check in `FlowCommandExecutor` as the last line of defence, since definitions can
change after a flow is scheduled.

## Risks

- Sub-flow admission moves onto the parent job's worker thread, so a blocked admission fails the
  parent job synchronously. This replaces the previous stuck-`CREATED` behaviour.
- The candidate snapshot is built inside the creation transaction but before admission takes the
  definition lock; the definition is read once per trigger, matching the previous contract for cron
  and manual runs.
- A crash before commit leaves no child and the parent launch gate settles the job run. A crash
  after commit but before the callback is persisted leaves an admitted child and yields a parent
  error, the same recovery shape as today.

## Slices

1. Extract `FlowRunCreationService` from `JobFlowRunner` and `FlowScheduleService`; behaviour
   unchanged apart from the removed reservation branch.
2. Switch `FlowCommandExecutor` to synchronous creation and remove its Quartz reservation.
3. Move `JOB_LIST` creation into `LegacyJobListService` and route both adapters through the
   creation service.
4. Update the state documentation and the affected tests.

## Verification

```sh
./mvnw -pl flink-platform-web -am compile
./mvnw -pl flink-platform-web -am -Dtest='FlowRunCreationServiceTest,FlowCommandExecutorTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

Compilation must include the repository's formatting, Checkstyle, and NullAway checks.
