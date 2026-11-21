# Flow-run state machine migration

> **Status:** Implemented in the current code. The rollout checklist below applies when upgrading
> a deployment from a build that wrote flow-run status without conditional transitions.

## Current contract

`FlowRunTriggerService` builds a complete run from a Quartz, manual, or sub-flow trigger, passes it
to `FlowRunCreationService`, and publishes the creation, block, and waiting-timeout outcomes to
logs and alerts. The transactional admission step locks the flow definition, reconciles expired
waiting runs, applies admission rules, and inserts the new run. The initial persisted state is
`WAITING` or `SUBMITTED`; no trigger creates a flow-run `CREATED` reservation. A retried admission
clears any id populated by a rolled-back insert.

For every flow-run type, `FlowRunStateService` owns later status transitions. Conditional database
updates arbitrate the source status and, for executor transitions, the owning host. A successful
transition owns its alert or cleanup side effects. `KILLING` is durable intent: new child
reservations are fenced, cleanup is retried, and the run becomes `KILLED` after its child rows
appear terminal. This does not prove that a previously reserved external command has stopped.
Worker reassignment uses a conditional host and status update: `reassignFlowRunsOf` moves a candidate
only if it is still owned by the observed dead host and remains non-terminal. Candidates are grouped
by target host and written in bounded batches. The returned update count reflects rows actually moved.

`JOB_LIST` remains on `LegacyJobListService` for creation: it checks for an active start-job run
and builds the single-job DAG. Existing and new `JOB_LIST` runs then use the same conditional
execution, kill, cleanup, and host-reassignment paths as other flow runs. The `JOB_LIST` type and
its creation interface remain available while production definitions are migrated. Existing
flow-run `CREATED` rows are still legacy data; they are not activated by this implementation.
The job-run state machine is outside this change. The parent flow's reservation transaction
prevents creation of new child rows after it enters `KILLING`, and the parent retries cleanup of
non-terminal children. Job-run submission claims `CREATED -> SUBMITTED` with a conditional
update before the external command starts, so duplicate process RPCs cannot submit the same run
twice. A child reserved before flow kill can still start after the kill request. The executor
callback updates only a row that is still unfinished, so it cannot restore an already committed
terminal state. Any failure after the claim is settled by the flow-side caller as `SUBMITTED ->
ERROR`; an exception during `exec` requests cancellation best-effort first. A lost reply or a
worker crash cannot be told apart from a confirmed local failure, so such a run is also written
`ERROR`; the external resource may already exist.

Job-run status writes stay gated by the persisted row: `JobRunStateService.updateFromReport`
writes a non-terminal observation only from a `SUBMITTED` or `RUNNING` row and a terminal
observation from any unfinished row; `CREATED` and `KILLING` observations are never written.
`completeWithError` accepts `CREATED` and `SUBMITTED`.
A worker report is authoritative once the run is claimed; a flow-side local exception may still
settle `CREATED` or `SUBMITTED` as `ERROR`. Before starting the command, the worker re-reads
the claimed row and skips a run that is no longer `SUBMITTED`. Kill requests are handled by
`KillJobService`; a kill that races after this read may miss a resource that has not started yet.
While waiting for completion, `JobExecuteThread` adopts the committed status returned by a rejected
observation and keeps polling until it is terminal; `KILLING` does not end the wait.
`KillJobService` writes `KILLING` before requesting external cancellation, then conditionally
writes `KILLED`.
The Flink YARN executor retries the kill request against resource manager failures, treats an
unregistered or already terminal application as nothing to stop, and does not wait for the
application to reach a terminal state. `KILLED` records the best-effort cancellation request, not
confirmed external exit.
A resource that outlives the request needs manual cleanup using the deterministic application tag;
the normal kill endpoint skips terminal job runs.

## Rollout and remaining work

Conditional transitions are unconditional in this build; there is no lifecycle switch. Deploy it
under coordinated quiescence, because a worker still running the previous binary writes the status
without a fence and therefore cannot participate in the guarantee. Before the first start, confirm
that no non-terminal row lacks an owning host (`host IS NULL` for `SUBMITTED`, `RUNNING`, or
`KILLING`); otherwise the host fence rejects that run's start, completion, and reassignment.
Reconcile residual flow-run `CREATED` rows before activation: rows reserved by the old sub-flow
path have an empty `host`. Set each to `FAILURE`, or `KILLED` when a parent job still tracks it,
and re-run `SELECT id, flow_id, host, create_time FROM t_job_flow_run WHERE status = 'CREATED'`
until it returns nothing. No schema migration is needed.

### Pre-upgrade data reconciliation

The host-fenced transitions and every host-scoped scan (`listExecutableRunsOnHost`,
`listWaitingRunsOnHost`, `listKillingRunsOnHost`) ignore a row whose `host` is empty. Such a row is
invisible to dispatch, waiting advancement, and kill cleanup, so it never reaches a terminal state.
Run these statements in the same quiescence window, before the first start:

```sql
-- 1. Detect. Expect zero rows.
SELECT status, COUNT(*) AS runs
FROM platform.t_job_flow_run
WHERE (host IS NULL OR host = '')
  AND status IN ('CREATED', 'WAITING', 'SUBMITTED', 'RUNNING', 'KILLING')
GROUP BY status;

-- 2. Legacy sub-flow reservations were never activated. Set KILLED instead when a parent
--    SUB_FLOW job run's back_info still references the child id.
UPDATE platform.t_job_flow_run
SET status = 'FAILURE', end_time = NOW()
WHERE (host IS NULL OR host = '') AND status = 'CREATED';

-- 3. WAITING/SUBMITTED rows have no owner and no started command; assign a live worker.
UPDATE platform.t_job_flow_run
SET host = '<live-worker-ip>'
WHERE (host IS NULL OR host = '') AND status IN ('WAITING', 'SUBMITTED');
```

For `RUNNING` or `KILLING` rows with an empty host, inspect each row before acting: the external
resource may still exist. Settle it as `FAILURE`, or `KILLED` when a parent job run tracks it; do not
assign a host blindly. Re-run statement 1 until it returns nothing.

Unit tests cover the transition matrix, kill orchestration, admission, and dispatcher behavior. The
affected Maven reactor must compile with formatting, Checkstyle, and NullAway checks. Runtime
verification of admission, concurrent kill/completion, stale owner callbacks, and launch cleanup
remains a follow-up.
