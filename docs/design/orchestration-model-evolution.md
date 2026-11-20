# Orchestration Failure Contract And Evolution

> **Status:** Design discussion captured 2026-11-20. Classification direction and local abort are
> implemented in the working tree, pending owner review. Reload throttling, visibility, and the
> evolution seams below are proposals. This document records decisions and rejected alternatives;
> it does not change the implemented behavior.

Builds on [short database outage recovery](short-database-outage-recovery.md) and
[major incident recovery](major-incident-recovery.md).

## Failure contract

The orchestration layer decides only one thing for a run: whether the next persisted decision can
be made. Every step yields three outcomes.

| Outcome | Meaning | Action |
| --- | --- | --- |
| `PROGRESS` | The step advanced persisted state | Continue; reset the reload backoff |
| `UNKNOWN` | The platform metadata store could not be read or written, so the decision is unknown | Abort the local attempt, release the slot, keep run state, do not spend business retry budget |
| `KNOWN_FAILURE` | An observed task failure, or a deterministic data/code error | Business `ERROR` / configured retry |

Rules that follow from this contract:

- An `UNKNOWN` outcome is never written as a failure. `FAILED` means the platform observed a
  failure; it does not mean "we could not find out".
- Reload is a resume, not a retry. It reads persisted state again and continues; it never relaunches
  external work. `CREATED -> SUBMITTED` is committed before an external command starts, and an
  uncertain write is re-read before it is repeated.
- The outer reload has no attempt-count or duration limit, because a durable counter cannot be
  written while its store is unavailable. The bound belongs on cadence and visibility, not on the
  count.
- Task execution retries stay inner and bounded (owner policy: maximum retries). The two layers do
  not share a budget.

## Classification policy

`MetadataFailure` answers one question: did this exception leave the decision unknown? The default
is "unavailable". Only exceptions that prove the operation failed for a deterministic reason are
excluded.

| Exception family | Classification | Reason |
| --- | --- | --- |
| `DataAccessException` (default) | unavailable | Infrastructure/data-access failure; reload may succeed |
| `TransactionException` | unavailable | A transaction could not start, commit, or complete |
| gRPC `UNAVAILABLE`, `DEADLINE_EXCEEDED` | unavailable | Peer or metadata unreachable within the deadline |
| `DataIntegrityViolationException` | deterministic | Constraint violation depends on data/code, not availability |
| `InvalidDataAccessResourceUsageException` | deterministic | Bad SQL/object usage; also covers `TypeMismatchDataAccessException` |
| `InvalidDataAccessApiUsageException` | deterministic | Programming error, not an outage |
| `PermissionDeniedDataAccessException` | deterministic | Grants must change before a retry can succeed |
| `DataRetrievalFailureException` | deterministic | Result-shape/data mismatch; covers `EmptyResultDataAccessException` |
| `UncategorizedDataAccessException` | **unavailable** | Literally "unknown": MyBatis/Spring could not classify the SQL error |
| `DataSourceLookupFailureException` | **unavailable** | Data source or JNDI lookup failure is infrastructure |
| `CleanupFailureDataAccessException` | **unavailable** | Conservative: cleanup failure may be transient |

### Rejected: classify by `NonTransientDataAccessException`

Spring's `NonTransientDataAccessException` is not a reliable "deterministic" marker:

- `DataAccessResourceFailureException` (and its `CannotGetJdbcConnectionException` subclass) is
  non-transient but is the most typical outage.
- `UncategorizedDataAccessException` is non-transient and is the "unknown" bucket; MyBatis wraps
  unmapped failures in `MyBatisSystemException`, and Spring JDBC in `UncategorizedSQLException`.
- `DataSourceLookupFailureException` is non-transient and is infrastructure.

A rule of `NonTransient && !DataAccessResourceFailureException` would therefore classify real
outages as deterministic failures. The explicit list above is the safer default because an
unrecognized deterministic exception is visible as a repeated abort, while an unrecognized outage
silently becomes a business failure.

### gRPC boundary

Only `UNAVAILABLE` and `DEADLINE_EXCEEDED` mean the metadata decision is unknown. `INTERNAL`,
`FAILED_PRECONDITION`, `NOT_FOUND`, and `INVALID_ARGUMENT` are observed outcomes. A failure of a
user-configured SQL datasource must never be reported as `UNAVAILABLE`/`DEADLINE_EXCEEDED`; it is a
job result. A status-poll deadline is metadata access and may be classified as unavailable.

## Reload cadence and visibility (proposed)

The coordinator scan is the reload loop (currently every 2 seconds). Without a throttle, a flapping
database or a misclassified deterministic error rebuilds a short-lived attempt every scan. Both are
visible and cheap to bound.

- Per-run, in-memory exponential backoff: 2s, 4s, 8s, 16s, 30s cap. Reset on `PROGRESS` and on
  process restart. No schema change, no persisted counter, no new status.
- The coordinator's scan failure already gates the fully-unavailable case: if the scan cannot read
  the database, nothing is dispatched.
- Include the causing exception type in abort logs.
- Alert on "scan succeeded, but the same run aborted `N` consecutive times". A real outage fails the
  scan itself, so this rule fires only for flapping or misclassification.
- Do not mark a run failed because an outage was long (`major-incident-recovery.md`). Long outages
  use the operator hold, not an automatic terminal state.

## Industry comparison

| Platform | Orchestrator DB unavailable | Unknown outcome | Task failure |
| --- | --- | --- | --- |
| Airflow | Scheduler loop logs and retries; a healthy scheduler takes over | Zombie threshold marks the task failed and retries it | Terminal failure or `up_for_retry` |
| DolphinScheduler | Master loop logs and continues; ZK/etcd failover | Worker heartbeat loss re-dispatches the task as fault-tolerant | Per-task retry plus workflow failure strategy |
| Prefect | Client retries API calls; the server cannot record state | Heartbeat loss marks the run `Crashed`, distinct from `Failed` | Terminal or configured retries |
| Dagster | Daemon loop retries on its next interval | Run monitoring judges by process/heartbeat and max runtime | Terminal `FAILURE` or `RetryPolicy` |

Common pattern: the orchestrator's own database errors are never translated into task failures; an
unknown outcome is resolved by a lease/heartbeat threshold; user failures are a separate state. The
cost of a bounded rule is a possible failure or duplicate execution for a task whose external work
actually succeeded. These platforms accept that trade and require idempotent tasks. This project
chooses the opposite trade: keep the outcome unknown and make it visible.

## Evolution seams

The current core is the long-term asset. Do not rewrite it; extract these seams in place, keeping
the current implementation as the default. Each seam is a small refactor with no behavior change.

| Seam | Current coupling | Target |
| --- | --- | --- |
| Trigger origin | Quartz is both trigger and cluster lock | Admission receives a trigger origin and idempotency key; Quartz is one source |
| Dispatcher | `FlowRunCoordinator` scans every 2s and submits in place | A `RunDispatcher` with the scan as the default implementation |
| Time semantics | Timezone open; no explicit Quartz misfire instruction; catchup is a manual `scheduleTime` | A schedule calculator owning timezone, misfire, and catchup together |
| Concurrency | Worker thread count plus a per-run semaphore | A `ConcurrencyPolicy` supporting flow/tenant pools and priority |
| Executor contract | Status polling and per-executor ad hoc handling | `submit` / `cancel` / `reconcile` with an attempt epoch |

Capability gaps that follow from the seams (proposed priority):

- P1: misfire/catchup, timezone and business date, event/sensor triggers, backfill planning,
  task-level timeout/SLA, flow and global pause, history archival.
- P2: dynamic task mapping, asset-aware scheduling, DAG versioning, cross-DAG lineage, a bounded
  `UNKNOWN` state with fencing, multi-tenant quotas.

Compatibility constraints for the evolution:

- New status values: ship a reader that tolerates unknown values before writing them.
- New gRPC fields: add optional fields first, enable them later.
- New trigger/dispatcher implementations: default implementation preserves current behavior,
  switched by configuration.
- New columns: nullable with defaults, released separately.

## Open questions

- Whether `UnexpectedRollbackException` and `IllegalTransactionStateException` should be
  deterministic. A rollback may be caused by a transient inner failure, so reload is defensible;
  a repeated deterministic rollback would loop.
- Whether the reload backoff belongs in this increment or the next.
- Alert thresholds and ownership for the consecutive-abort signal.
- Whether a bounded `UNKNOWN` state is required before a public release.
