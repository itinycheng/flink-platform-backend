# Flow-run completion after job-run settlement

> **Status:** Revised proposal for owner review, 2026-11-17. Job-run conditional writes are
> implemented; the flow lifecycle changes below are not.

## Commit boundary

**Implemented job-run state increment.** `JobRunStateService` conditionally writes status changes
and returns `APPLIED`, `REJECTED`, or `NOT_FOUND`. On rejection, the service reads the committed
row; `JobExecuteThread` adopts that status when processing a worker report. This increment also
introduced `requestKill` and `confirmKilled`: `KILLING` records durable job-run cancellation
intent before the kill command, and `KILLED` follows the command. The flow-run state machine is
unchanged. Corrections to existing commits use new commits unless the owner explicitly directs
otherwise.

**Flow completion increment (future commit, after review).** A normal DAG run waits for its
created job runs to reach persisted terminal states, then computes and records the flow result.
Explicit flow cancellation continues to use `RUNNING -> KILLING -> KILLED`. This increment is
one coherent behavior change across the job wait and flow completion paths. It needs no new
status, schema, REST, or gRPC contract.

## Future increment: exact behavior

1. **Wait for committed job status.** `JobExecuteThread` reads the persisted job-run status
   during each wait iteration, including when the worker report matches its cache or the status
   RPC fails. `KILLING` no longer becomes a synthetic `KILLED` vertex result. While the parent
   remains `RUNNING`, the job thread retries that row's existing idempotent `KillJobService`
   operation; only a persisted terminal status releases the job future. For a parent already
   `KILLING`, the existing flow cleanup keeps driving child cancellation. After worker restart,
   `FlowRunCoordinator` re-dispatches an unfinished `RUNNING` flow and its job thread re-adopts
   the persisted child run.
2. **Complete only after child settlement.** The owning flow worker checks that its DAG has no
   executable or running vertex and that no created child job run, including earlier attempts,
   remains unfinished. The completion operation locks the parent flow row before the child
   check. `JobRunExtraService.createJobRunIfOwned` already uses that same lock and requires a
   `RUNNING` parent, so a late child cannot be inserted after completion wins. If the check
   fails or ownership changed, release the flow execution slot; the coordinator can re-adopt a
   still-`RUNNING` flow. There is no busy wait inside completion.
3. **Keep the existing flow state edges.** For `SUCCESS`, `FAILURE`, or `EXPECTED_FAILURE`, use
   the existing owned `completeRunning`. For a completed DAG whose final result is `KILLED`,
   use a small completion operation that, under the lock and after the empty-child check,
   applies the existing owned `requestKill` transition and then `confirmKilled` in the same
   transaction. It calls the state operations, not `FlowRunKillService.requestKill`, so it sends
   no child kill request. Externally the transaction commits only the final `KILLED` row. The
   definitions and permitted edges of `requestKill` and `confirmKilled` stay unchanged.
4. **Reserve force for the force endpoint.** When normal `FlowRunKillService` cleanup sees no
   unfinished child, call `confirmKilled` instead of `forceKilled`. Leave the administrative
   force endpoint as an explicit exception.

Job runs are created as DAG predecessors finish; vertices whose preconditions never match have
no job run. The child check therefore covers all *created* attempts, not every DAG vertex.

## Scope and limits

Expected files for the future increment: `JobExecuteThread`, `FlowExecuteThread`,
`FlowRunStateService`, `FlowRunKillService`, and a small `JobRunInfoService` existence query;
roughly 80–130 changed lines. Do not move API cleanup to an executor, remove
`JobRunTransition.NOT_FOUND`, change the UI, or add a global job-run scanner in this commit.

The existing system cannot guarantee settlement when the job's worker stays unreachable.
Under this plan that child and its parent remain nonterminal. A durable kill deadline and
external-cleanup record are a separate reliability feature if bounded settlement becomes a
requirement; they are not needed to untangle the normal completion path.

Compile the affected Maven reactor with its formatting, Checkstyle, and NullAway checks. Review
a direct job kill that fails once and retries, explicit flow kill, completion racing with late
child creation or another kill, and worker restart during `KILLING`. Add focused automated tests
only after owner authorization under `AGENTS.md`.
