# Short platform database outages

> **Status:** Flow and Job orchestration fail-fast handling is implemented in the working tree,
> pending owner review, 2026-11-20. Worker-side persistence and external execution recovery
> remain proposed.
> This covers automatic continuation of the same persisted runs after a recoverable outage.
> Long incidents and operator release are in [major-incident recovery](major-incident-recovery.md).

## Contract

MySQL owns run status, ownership, admission, and retry history. Here "database" means the
platform metadata datasource and Quartz store; failure of a user-configured SQL job datasource
remains a job execution result. A metadata exception makes the attempted decision unknown,
not a business failure. No new persisted FlowRun or JobRun status is needed while MySQL is down.

| Outcome | Behavior |
| --- | --- |
| Metadata read throws or times out during an existing FlowRun | Abort the local Flow orchestration attempt without changing persisted run state or spending task retry budget. The coordinator can reload the same Run ID on its next scan. |
| Metadata write throws or times out | Its commit outcome is unknown. Do not blindly repeat the write or its external side effect; reconcile the same Run ID after connectivity returns. |
| JobRun lookup succeeds but the row is absent | Return `ERROR` for this attempt and apply its configured business retry. Log the missing ID. This accepts possible overlap with an untracked external task. |
| Parent FlowRun lookup succeeds but the row is absent | Abandon child orchestration and alert; there is no parent for another reservation. |
| A persisted row is read successfully | Continue its existing conditional state and retry rules. |

## One end-to-end failure and recovery path

1. **Before a Run exists:** Quartz, manual, or sub-flow admission reads and writes MySQL in a
   short transaction. The current bounded transaction retry remains unchanged. Active-run
   conflict checks normally reject a repeated occurrence; the rare ambiguous commit outcome
   is accepted for now and is outside this orchestration change.
2. **While the Flow runs:** the owning node reads FlowRun/JobRun state before it reserves a
   child, advances the DAG, or decides on a business retry. A metadata exception aborts the
   **local Flow orchestration**, not the persisted FlowRun or JobRun. All sibling DAG workers
   must stop issuing new decisions and their callbacks must be fenced before the Flow slot
   is released. Already-running external commands are not killed merely because MySQL is down.
3. **At the worker:** `CREATED -> SUBMITTED` is committed before the external command starts.
   If a later callback write fails, the worker keeps the observed result for a bounded
   in-process retry while it is alive; the caller retains the same JobRun identity. If the
   worker or reply is lost, that JobRun is uncertain, not a confirmed business `ERROR`.
4. **When MySQL returns:** the coordinator can redispatch persisted nonterminal FlowRuns after
   checking ownership. Read the same JobRuns and continue safe work from those rows. Re-read
   an uncertain write before repeating it, and locate an uncertain external task by the same
   JobRun identity. A run whose external outcome cannot be established stays unresolved with
   an alert; other safe runs need not wait for it.

This is a safety-first recovery policy. Automatic continuation applies only when persisted
state or the external resource can be identified. It does not promise that every run completes
automatically after a database outage.

## Current path and gaps

Quartz, manual execution, and sub-flow commands enter `FlowRunTriggerService`. Transactional
admission creates a `WAITING` or `SUBMITTED` FlowRun. `FlowRunCoordinator` scans rows owned by
this host and dispatches `FlowExecuteThread`. Its DAG threads load, create, submit, and poll
JobRuns. The worker claims `CREATED -> SUBMITTED` before starting an external command.

- `JobExecuteThread` now propagates metadata exceptions from its initial reads, Run lookup and
  creation, and status persistence. Other job execution errors keep their business `ERROR` path.
- `FlowExecuteThread` now stops DAG expansion on an aborted Job or exceptional future and waits
  for local sibling futures before releasing its slot. The coordinator can redispatch the same
  persisted Run ID after that release.
- `checkFlowAction` remains a best-effort parent check through `getLiteByIdOrNull`; its failed
  read does not abort this attempt. The owner and worker execution boundary remain separate
  concerns.
- `FlowRunCreationService` retains its bounded admission retry as accepted by the owner.
- `ProcessJobService` claims `SUBMITTED`, invokes the command, and writes its callback later.
  On callback write failure its `finally` block requests cancellation best-effort. The caller
  can then receive an error although the external task may have started. Current code has no
  durable callback replay or complete external-resource reconciliation.

## Local abort and natural redispatch

Flow and Job orchestration use the coordinator's existing scan as their retry loop. A metadata
exception ends the local execution attempt; the same nonterminal FlowRun remains available for
the next scan after its local threads have stopped and its slot is released. If MySQL remains
unavailable, the coordinator scan fails without dispatching new work. Do not add a per-read
retry, per-Flow cooldown, new persisted state, or node-local probe gate for this path.

This policy starts after a FlowRun has a durable ID. Admission before that point has an
ambiguous-insert problem and needs its own trigger identity. Worker execution also needs a
separate policy around a claimed JobRun and any already-started external command.

`FlowExecuteThread` prevents new vertices after local abort, fences sibling callbacks, and
waits for their futures before releasing its in-memory slot. No FlowRun or JobRun terminal
status is written for this abort. A blocking gRPC call can delay that local cleanup.

Operational health and alerting can be added separately. The UI and database-backed alerts
may also be unavailable during the outage; a liveness restart is not the normal response.

## Continue the same run after recovery

On the next successful coordinator scan, refresh persisted FlowRun status and host. An old
owner abandons a reassigned or terminal run. The coordinator re-adopts nonterminal FlowRuns
on the current host. A new local DAG execution reloads existing JobRun IDs and states and
continues without spending business retry budget. This also works after process restart.

A recovered database does not resolve an ambiguous external submit. After a lost RPC reply
or callback write failure, query the same JobRun and external identity before another launch.
Flink/YARN has a deterministic JobRun tag; local executors need a separate recovery rule.
If the worker survives the outage, it can retry persistence of the result it observed in
memory. If it dies before that write, a local subprocess result may be unknowable without a
separate durable callback record. Hold that JobRun and alert rather than manufacture a new
attempt. Do not block unrelated FlowRuns after database and ownership checks succeed.
In a one-node database partition, peers may reassign the FlowRun after heartbeat expiry.
The old node checks ownership on recovery. That fences DAG decisions, not an already-running
shell process or YARN application; executor self-fencing remains separate work.

Quartz triggers still pass through transactional admission. Verify actual Quartz misfire and
`scheduledFireTime` behavior for a short outage; this design does not automatically replay
every missed cron tick. Long-incident catch-up is specified in the linked proposal.

## Reviewable increments

1. **Flow safe exit (implemented, pending review):** an `ABORTED` Job or exceptional future
   stops local DAG expansion; sibling Job threads observe the abort, and the Flow slot is
   released only after their futures finish.
2. **Orchestration database failures (implemented, pending review):** metadata exceptions in
   Flow/Job execution abort locally rather than becoming business `ERROR`. No per-read retry is
   added; the coordinator reloads the same persisted Run. A missing JobRun row remains an error.
3. Handle worker-side database failures at the claim and callback boundaries without blindly
   repeating external execution. Operational health and alerting are separate increments.
4. Prevent callback-persistence failure from becoming a business retry or automatic external
   kill. Unknown outcomes remain visibly unresolved; no new persisted run status is required.
5. Add stable trigger identity for ambiguous FlowRun creation and per-executor reconciliation
   of JobRun submission and callback persistence. Start with the Flink/YARN tag; define the
   conservative manual outcome for local executors before promising automatic completion.

Review failures before and after admission commit, after JobRun claim, after command launch
but before callback persistence, during parallel DAG execution, and under one-node database
partition. Focused failure/concurrency tests need owner authorization under `AGENTS.md`.
The separate [flow-run settlement proposal](flow-run-terminal-settlement.md) is not implied
by this design.

References: [Airflow scheduler and database health](https://airflow.apache.org/docs/apache-airflow/stable/administration-and-deployment/scheduler.html#running-more-than-one-scheduler),
[DolphinScheduler service failover versus task retry](https://github.com/apache/dolphinscheduler/blob/3.2.2/docs/docs/en/architecture/design.md),
and [Dagster adoption of an existing run](https://docs.dagster.io/deployment/execution/run-monitoring).
