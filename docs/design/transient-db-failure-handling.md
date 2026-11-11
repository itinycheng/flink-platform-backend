# Transient Database Failure Handling in Flow Execution

> **Status:** Proposed. Phase 1 (JDBC timeouts, exception classification, bounded retry on
> idempotent operations) is designed here. Phase 2 (creation-path retry and insert idempotency)
> is described but explicitly out of scope for the first implementation.

## Problem

A momentary database connectivity failure during flow execution can currently become a permanent
business outcome:

- `JobExecuteThread.callOnce` catches every `Exception`, sets the in-memory job status to `ERROR`,
  and persists it (`JobExecuteThread.java:185-189`). A failed `jobInfoService.getOne`, a failed
  `jobRunInfoService.getById`, or a failed read after `processRemoteJob` therefore marks the job
  failed even though the job itself never ran or is still running remotely.
- `JobExecuteThread.get` retries initialization only three times with a fixed one-second sleep
  (`JobExecuteThread.java:86-103`, `192-229`). A database outage longer than roughly three seconds
  returns `ERROR` for the job.
- `FlowExecuteThread.completeAndNotify` persists the final flow status without any retry
  (`FlowExecuteThread.java:132-143`). On failure the thread exits and the run stays `RUNNING`.
  The dispatcher eventually re-adopts it, but only after a full polling interval and with an
  unnecessary re-execution window.
- The JDBC URLs in `application-dev.yml:32` and `application-docker.yml:31` set no
  `connectTimeout` or `socketTimeout`. Connector/J defaults to no socket timeout, so a network
  black hole hangs the query indefinitely and no retry can ever fire.

Adding a blanket retry to every DAO call is not the answer:

- Retrying inside an active transaction retries a doomed transaction.
- Retrying a non-idempotent insert can duplicate rows and cause duplicate job execution
  (`JobRunExtraService.createJobRun` uses a plain `save`, and `t_job_run` has no unique index).
- Retrying business rejections (job offline, dependency unsatisfiable, invalid configuration)
  is meaningless and can mask real failures.
- `FlowExecuteThread` and `JobExecuteThread` are instantiated with `new`
  (`FlowRunCoordinator#submitToExecutor`, `FlowExecuteThread#execVertex`), not obtained from the Spring
  container, so annotation-based retry (`@Retryable`) cannot apply to them.

## Goals and Non-Goals

Goals:

1. A transient database or network failure does not produce a terminal job or flow state.
2. Failures surface quickly instead of hanging indefinitely.
3. Retries never duplicate a state transition, a row, or an external job submission.
4. The change is observable: every retry and every exhausted retry is logged with its operation.

Non-goals:

- Automatic database failover, circuit breakers, or connection-pool replacement.
- Changing the job-level retry contract (`BaseJob.retryTimes` / `retryInterval`).
- Retrying non-idempotent inserts in Phase 1.
- Changing worker failover semantics (`WorkerHeartbeat.reassignOrphans`).

## Failure Taxonomy

| Category             | Examples                                                                                | Behavior                                               |
|----------------------|-----------------------------------------------------------------------------------------|--------------------------------------------------------|
| Transient DB/network | connection reset, socket timeout, deadlock, lock wait timeout, pool acquisition failure | retry with bounded backoff; never a terminal job state |
| Permanent DB         | bad SQL, constraint violation, permission denied, schema mismatch                       | fail fast; do not retry                                |
| Business             | job offline, no worker available, dependency unsatisfiable, invalid config              | existing behavior; do not retry                        |

Retryable when the cause chain contains any of:

- `java.sql.SQLRecoverableException`, `SQLTransientException`, `SQLTimeoutException`
- `java.net.SocketTimeoutException`, `java.net.ConnectException`
- `com.mysql.cj.jdbc.exceptions.CommunicationsException`
- Spring `TransientDataAccessException`, `RecoverableDataAccessException`, `QueryTimeoutException`,
  `CannotAcquireLockException`, `DeadlockLoserDataAccessException`, `PessimisticLockingFailureException`
- Spring `DataAccessResourceFailureException` explicitly. It extends a non-transient base class but
  is the shape Spring commonly produces for a lost connection, so class-hierarchy checks alone miss it.

Non-retryable: `BadSqlGrammarException`, `DataIntegrityViolationException`, `IncorrectResultSizeDataAccessException`,
and any exception that is not a database/network failure.

Unknown `DataAccessException` subclasses are treated as non-retryable but logged at `WARN` with their
type, so the allowlist can be extended from evidence rather than guesswork.

## Design

### 1. Connection and query timeouts

Add bounded timeouts to the MySQL URL in every profile:

```text
connectTimeout=5000&socketTimeout=60000&tcpKeepAlive=true
```

- `connectTimeout` bounds pool/network establishment.
- `socketTimeout` must exceed the slowest legitimate query. The flow-execution path only performs
  point reads and short updates; reporting queries are the ones to check before choosing the value.
- This is the prerequisite for every other measure: without it a hung query never throws, so no
  retry logic runs.

### 2. Transient-failure classifier

Add one small utility in `flink-platform-web` (next to `AppRunningRetryPredicate`):

```java
public final class TransientFailures {

    public static boolean isTransient(Throwable throwable);
}
```

It walks the cause chain with a depth guard and applies the allowlist above. It does not inspect
SQLState or vendor codes in Phase 1; the type allowlist plus WARN logging is enough to start and
is cheap to extend.

### 3. Bounded retry helper

Add one helper used explicitly at call sites:

```java
public final class BoundedRetry {

    static <T> T call(String operation, Supplier<T> action);

    static void run(String operation, Runnable action);
}
```

Contract:

- At most 3 attempts, backoff 200 ms / 500 ms / 1 s.
- Retries only when `TransientFailures.isTransient` is true; rethrows anything else immediately.
- Aborts early when `AppRunner.isStopped()`.
- Logs each retry at `WARN` with operation name and attempt; throws the last exception on exhaustion.
- Must be invoked outside a transaction. Call sites own that boundary.

A hand-written helper is deliberate: the affected executors are not Spring beans, and a static
helper keeps retry policy visible at the exact failure point instead of hidden in a proxy.

### 4. `JobExecuteThread`

- `callOnce`: wrap the idempotent reads (`jobInfoService.getOne`, `jobRunInfoService.getById`,
  `getOrCreateJobRun`'s existing lookup, the read after `processRemoteJob`) and
  `updateJobRunIfNeeded`'s update in `BoundedRetry`.
- After retry exhaustion inside `callOnce`, distinguish the two outcomes:
    - transient DB failure: do **not** set `jobRunStatus = ERROR` and do **not** persist `ERROR`.
      `callOnce` throws a dedicated `TransientDbUnavailableException`; `get` catches it, logs it,
      sleeps, and continues the loop without consuming the job's retry budget;
    - any other exception: keep today's behavior (`ERROR` + `backInfo`).
- `get` loop: transient DB failures do not consume the job's `retryAttempt` budget. They sleep and
  continue until the DB recovers or the application stops. The flow-level timeout (`FlowExecuteThread.handleTimeout`)
  remains the operator-facing bound. This is intentional: a
  false `ERROR` is worse than a run that visibly waits.
- Replace the two hand-written three-attempt loops (`getRetryConfig`, `getCountAndLastJobRun`) with
  `BoundedRetry`. On exhaustion during initialization, do not return an `ERROR` response; sleep and
  re-enter initialization while `AppRunner.isRunning()`.

Eventual consistency keeps this safe: the remote job status is the source of truth, so a lost
status write is corrected when the run is re-adopted and the remote status is polled again.

### 5. `FlowExecuteThread`

- Wrap the initial `updateById` and `completeAndNotify`'s `updateById` in `BoundedRetry`.
- On exhaustion, allow the exception to end the thread. This is the existing, documented recovery
  path: the run remains non-terminal and the dispatcher re-adopts it. Starting the flow thread again
  is safe because each job re-reads its existing `CREATED`/non-terminal job run and re-adopts it
  rather than inserting a duplicate.
- Do not retry the in-memory DAG bookkeeping; only the two database writes.
- `FlowExecuteThread` checks the in-flight host cached by `FlowRunCoordinator`. The coordinator
  refreshes hosts from the database in batches every 10 seconds. If a batch read fails, that batch
  and later batches keep their cached hosts until the next scheduled refresh. A missing or null
  cached host is treated as "still ours"; this does not fence execution during a database outage.

### 6. `FlowRunCoordinator`

No change in Phase 1. `drainAndExecute` already logs and retries on the next tick (`FlowRunCoordinator#drainAndExecute`);
`advanceWaitingRuns` is re-invoked by `@Scheduled`; per-item
failure handling already isolates one bad run (`FlowRunCoordinator#advanceWaitingRuns`). Adding another
retry layer here would amplify load during a database incident.

### 7. Creation path (Phase 2, not implemented now)

- `FlowRunCreationService.tryCreateFlowRun` is transactional. Any retry must sit outside the
  transaction, and a failure after commit but before the caller observes success can create a
  duplicate run.
- `(flow_id, schedule_time)` cannot be made unique: a rerun after a terminal run is a legal,
  intended operation (see [schedule-time-aware concurrency](schedule-time-concurrency.md)).
  Idempotent creation therefore needs a trigger/reservation identifier, not that index.
- `createJobRun` cannot be retried blindly: multiple job runs per `(flow_run_id, job_id)` are legal
  because of `BaseJob.retryTimes`. An attempt/execution key is required before retrying the insert.
- Until then, creation failures stay as they are. They are not converted into partial runs.

## Contracts

- Only `TransientFailures.isTransient` failures are retried.
- Retry is bounded and in-process; no database-side retry and no infinite loop hidden in a helper.
- Retries only wrap idempotent reads and fixed-field/conditioned updates. Inserts and any call
  inside an open transaction are excluded.
- Conditional transitions (`updateStatusFromTo(from, to)`) keep their `from` predicate; retry must
  not turn them into unconditional writes.
- A transient failure never writes a terminal state.

## Risks

- `socketTimeout` too low aborts legitimate slow queries. Choose the value against the slowest real
  query per profile; 60 s is a starting point, not a universal answer.
- The transient allowlist may miss a driver-specific exception. WARN logging of unclassified
  `DataAccessException` makes the gap visible and the fix is a one-line addition.
- Retries add load during a database incident. Bounded attempts plus the dispatcher's existing
  2-3 second cadence keep amplification modest; do not add jitter-free tight loops.
- A prolonged outage leaves jobs non-terminal and waiting. Operators see retry WARN logs; flows with
  a configured timeout still fail through `handleTimeout`. Flows without a timeout have no
  automatic upper bound, which is the same as today for a hung query.
- Failover overlap remains possible (documented in [architecture](../architecture.md)); this design
  does not change fencing or submit idempotency.

## Slices

1. **Timeouts.** Add URL parameters to dev and docker profiles; document the production requirement.
   Observable: a killed connection throws within the timeout instead of hanging.
2. **Classifier and helper.** Add `TransientFailures` and `BoundedRetry` with WARN logging.
   Observable: unit-level behavior via focused tests, if authorized.
3. **Job execution.** Apply the helper to `JobExecuteThread`; split transient from terminal
   failure handling. Observable: with the DB briefly unavailable, a running job stays non-terminal
   and recovers instead of turning `ERROR`.
4. **Flow completion.** Apply the helper to `FlowExecuteThread`'s two writes. Observable: a failed
   final update no longer leaves a silently stuck `RUNNING` run without a retry attempt and log.

Each slice compiles independently. Do not start the next slice until the previous one is reviewed.

## Verification

Compile gate:

```bash
./mvnw -pl flink-platform-web -am compile
```

Focused runtime check (manual, with the dev profile):

1. Start a flow with a long-running job.
2. Kill the application's MySQL connections (or pause the container) long enough to exceed one
   attempt but not the flow timeout.
3. Confirm the job and flow remain non-terminal, retries appear in the log with operation names,
   and both converge to the real remote outcome after the database returns.

Recommended tests, to be written only after owner approval:

- `TransientFailures` classifies `CommunicationsException`, `DataAccessResourceFailureException`,
  deadlock, and rejects `BadSqlGrammarException` and business exceptions.
- `BoundedRetry` stops after three attempts, rethrows non-transient immediately, and aborts when
  the application is stopping.

## Open Questions

1. Should the retry limits be fixed constants or a `worker.*` configuration knob? Constants are
   simpler; make them configurable only if operators need to tune for a slow database.
2. Should a flow without an explicit timeout get a default execution timeout now that transient
   failures can keep a run waiting indefinitely?
