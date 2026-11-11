# Flow-run state machine migration

Status: implementation migrated to `feat/state-machine`; activation remains an operational step.

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
non-terminal children. Job-run submission itself follows the original direct implementation:
there is no conditional launch claim. The executor callback now updates only an unfinished
job-run row, so it cannot restore an already committed terminal state. A child reserved before
flow kill can still start after the kill request, and duplicate process RPCs can execute it more
than once.
Job-run status writes stay gated by the persisted row: `StatusUpdate.WORKER_REPORTED` accepts
`CREATED`/`WAITING`/`SUBMITTED`/`RUNNING`, while `StatusUpdate.LOCAL_ERROR` accepts only `CREATED`.
A worker report is authoritative once the run is claimed; a local exception must not overwrite a
`SUBMITTED` or `RUNNING` row with `ERROR`.
Job-run kill uses the original direct `KILLED` write after requesting external cancellation;
it does not enter `KILLING` or wait for durable stop proof. Deployments requiring stronger launch
and cleanup guarantees need a separate job-run design.

## Rollout and remaining work

Conditional transitions are unconditional in this build; there is no lifecycle switch. Deploy it
under coordinated quiescence, because a worker still running the previous binary writes the status
without a fence and therefore cannot participate in the guarantee. Before the first start, confirm
that no non-terminal row lacks an owning host (`host IS NULL` for `SUBMITTED`, `RUNNING`, or
`KILLING`); otherwise the host fence rejects that run's start, completion, and reassignment.
Reconcile residual flow-run `CREATED` rows using the procedure in
[single-phase flow-run creation](flow-run-single-phase-creation.md). No schema migration is needed.

Unit tests cover the transition matrix, kill orchestration, admission, and dispatcher behavior. The
affected Maven reactor must compile with formatting, Checkstyle, and NullAway checks. Runtime
verification of admission, concurrent kill/completion, stale owner callbacks, and launch cleanup
remains a follow-up.
