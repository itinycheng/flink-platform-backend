# Sub-flow job-run cancellation

> **Status:** Parent/child association is implemented in the current code; waiting for child
> cancellation to finish remains proposed.

## Current behavior and two separate gaps

`ProcessJobService` claims the parent `SUB_FLOW` job run as `SUBMITTED` before calling
`FlowCommandExecutor.execCommand`. The executor creates a child flow run through
`FlowRunTriggerService`; `FlowRunCreationService` commits that run in its own transaction.
Before increment 1, only after `execCommand` returned did `ProcessJobService` write the
child run ID into the parent job run's `backInfo`. `FlowCommandExecutor.killCommand` reads
that field to find the child. A cancel between child creation and the callback write could
therefore miss the child; the parent job run could become `KILLED` while the child continued.

There is a second gap even when the child ID is available. Requesting cancellation of a
running child moves the child flow run to `KILLING`; `killCommand` then returns and
`KillJobService` immediately confirms the parent job run as `KILLED`. The parent flow can
treat that DAG vertex as finished before the child flow run is terminal. Finding the child
and waiting for its settlement are separate behaviors and should be reviewed separately.

## Increment 1: bind creation and parent association

The existing short flow-run admission transaction invokes a `FlowRunCreationHook` with
the saved `JobFlowRun` for `SUB_FLOW`. After inserting the child but before committing,
the hook updates the parent job run's `backInfo` only when its status is `SUBMITTED`. It does not check
the previous `backInfo`. An update that changes no row rolls back the child insert. The UPDATE
itself takes the parent-row lock; no preceding `SELECT FOR UPDATE` is needed. Ordinary
triggers pass a no-op hook, so admission rules are not duplicated. Do not wrap `execCommand` or
`ProcessJobService.processJob` in a transaction: `tryCreateFlowRun` rejects an active
caller transaction and its admission already owns a `REQUIRES_NEW` transaction.

The conditional UPDATE arbitrates creation against `requestKill`. If cancellation commits
first, the association update changes no row and the child is rolled back. If the
association update wins, cancellation waits for commit, then reads the child ID. A repeated
normal submission of the same job run is prevented by the existing `CREATED -> SUBMITTED`
claim; this increment does not add a second `execCommand` re-entry path. No schema change
is required for this increment.

`tryCreateFlowRun` routes `JOB_LIST` to a separate `LegacyJobListService` admission
transaction. Both admission branches now invoke the hook after inserting a run. Whether
`JOB_LIST` should remain a valid `SUB_FLOW` target is a separate validation decision.

`backInfo` holds the association, and a normal callback writes the same child ID.
`JobExecuteThread.completeWithError` still replaces the entire `backInfo` with an error
callback. This is an accepted limit of increment 1: if the compensation cancellation
also fails, the later error write can erase the committed child ID and leave no automatic
retry path. Preserve that association with a dedicated field or an atomic merge in a
separate follow-up. `JobRunStateService.completeWithError` and job-run status edges remain
unchanged. The transaction closes the creation/cancel race but does not guarantee
recovery from a failed cancellation after the parent becomes terminal `ERROR`.

If cancellation wins the conditional association update, the child insert rolls back.
`FlowCommandExecutor.execCommand` lets exceptions propagate. `ProcessJobService` then attempts
child cleanup in its `finally` block, and `JobExecuteThread.completeWithError` can mark a
`CREATED` or `SUBMITTED` parent job run as `ERROR`. It cannot change `KILLING` to `ERROR`;
the cancellation path remains responsible for confirming `KILLED`. Blocked admission and
non-runnable child definitions still return failure callbacks. A terminal `FAILURE` report
may settle a parent job run in `KILLING`; its precedence against cancellation remains a
separate decision.
If child cleanup fails after a committed creation, the `backInfo` preservation gap above
remains follow-up work.

Review cancellation before the association UPDATE, after admission commit but before the normal
callback write, a callback write failure, and a worker restart after child creation.

## Increment 2: confirm the parent only after child settlement

Keep the parent job run in `KILLING` while its child flow run is nonterminal. Repeated
cancellation requests should request child cancellation idempotently and inspect the
child's persisted status. Only a terminal child permits `confirmKilled` on the parent.
Prefer an explicit completed/pending result from the command kill path to treating normal
child cleanup as an exception; choose the exact interface change during this increment's
review. Other job types keep their existing completion behavior unless separately changed.

`FlowRunCoordinator` already retries cleanup every two seconds when the parent flow run
is `KILLING`. A directly killed job run whose parent flow remains `RUNNING` also needs a
driver, such as its job wait loop or a separate job-run `KILLING` scan. Do not block a
kill RPC until the child flow finishes. Worker restart must be able to recover the child
ID from the committed association. This increment changes when the parent DAG vertex
becomes terminal; it is distinct from the first increment's atomic write.

Child flow-run terminal status is the settlement condition here. As with current job-run
`KILLED` semantics, it does not independently prove that every external resource, such as
a YARN application, has stopped.

## Relationship to other work

The normal parent-flow completion proposal in
[flow-run-terminal-settlement.md](flow-run-terminal-settlement.md) waits for created job
runs to become terminal. It does not implement the `SUB_FLOW` child-settlement rule above.
Bounded recovery of a `KILLING` job run with an unreachable worker remains the separate
[KILLING job-run settlement](../../TODO.md#killing-job-run-settlement) item. Compile the
affected Maven reactor for each implementation increment; propose focused concurrency
checks for owner approval under `AGENTS.md` before adding automated tests.
