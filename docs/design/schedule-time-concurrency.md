# Schedule-Time-Aware Run Concurrency

> **Status:** Phase 1 implementation. This document separates logical-run identity from
> overlap and capacity policy. Phase 1 covers `JobFlowRun`; flow-less job runs and distributed
> atomic admission remain follow-up work.

## Problem

`schedule_time` identifies the logical business period of a run. It is intentionally independent
of `start_time`: queueing, worker saturation, Quartz misfire recovery, and failover may delay the
physical start without changing the period being processed.

The existing conflict check only looks for a non-terminal run with the same `flow_id` (or the same
manual `startJobId`). Consequently, a run for `2026-10-01` blocks a backfill run for `2026-09-01`,
even though they are different logical runs and may safely process different partitions.

## Separate the Three Concerns

1. **Logical duplicate** — start-job executions use `strategy + startJobId`; ordinary scheduled
   executions use the resolved `schedule_time`.
2. **Overlap policy** — whether different logical periods may be active together.
3. **Capacity** — how many active flow runs are allowed, independently of the number of vertices
   that may run inside one flow run.

`maxJobsPerRun` is the per-flow-run job limit. `maxActiveRuns` is the number of concurrent flow
runs and defaults to `1`, preserving existing behavior. A user-supplied `maxActiveRuns` is capped
by the administrator setting `worker.maxActiveRunsPerFlow` both during validation and again at
runtime, so stored configuration cannot bypass the platform ceiling.

## Phase 1 Admission Rules

Given all active runs of the same flow:

1. For `ONLY_CUR_JOB`, `ALL_POST_JOBS`, and `ALL_PRE_JOBS`, reject an active run with the same
   `strategy + startJobId` as `DUPLICATE_START_JOB`, regardless of `schedule_time`.
2. For an ordinary scheduled execution, reject another ordinary scheduled run with the same
   resolved `schedule_time` as `DUPLICATE_SCHEDULE`.
3. Otherwise, if the total active count for the flow is already `maxActiveRuns`, reject it as
   `MAX_ACTIVE_RUNS`.
4. Otherwise admit the run. Every task in the admitted flow run continues to inherit the same
   business-time anchor.

Start-job and ordinary scheduled executions have separate duplicate rules. They may coexist when
the flow has remaining capacity. Different start jobs or different strategies may also coexist;
the admission layer deliberately does not infer overlap from the DAG. Setting `maxActiveRuns`
above `1` therefore opts into possible overlap between the jobs executed by different flow runs.

The module boundary is deliberate: DAO `JobFlowRunService.listActiveRuns` only loads non-terminal
rows, while Web `JobFlowRunBizService` owns execution-scope, logical-time, and capacity decisions.

Examples for `maxActiveRuns = 2`:

| Active run | Candidate | Result |
|---|---|---|
| scheduled `2026-10-01` | scheduled `2026-10-01` | duplicate schedule, reject |
| scheduled `2026-10-01` | scheduled `2026-10-02` | admit |
| `ONLY_CUR_JOB(A)` | `ONLY_CUR_JOB(A)` | duplicate start job, reject |
| `ONLY_CUR_JOB(A)` | `ONLY_CUR_JOB(B)` | admit |
| two active runs | any third run | capacity reached, reject |

## Trigger Semantics

- Cron: candidate time is Quartz `scheduledFireTime`, not actual fire time.
- Manual run: an explicit request `scheduleTime` wins; otherwise the Quartz one-shot fire time is
  used. The override is request-only and is not part of `ExecutionConfig` or persisted `JobFlow`.
- Subflow: candidate time is inherited from the parent flow run.
- The effective value is persisted in `JobFlowRun.scheduleTime`, which is the canonical source for
  an existing run. `ExecutionConfig` does not contain logical-time state.
- Effective times are truncated to the database column's second precision before comparison and
  persistence.
- Dependency-wait deadlines and warnings use the run's physical `create_time`, not its logical
  `schedule_time`, so historical backfills receive a full wait window.

## Industry Parallels

- Airflow gives each DAG run a logical date/data interval, permits several logical intervals up to
  `max_active_runs`, and separately controls whether an existing logical date may be reprocessed.
- DolphinScheduler separates `schedule_time` from actual start time and exposes Parallel, Serial
  Wait, Serial Discard, and Serial Priority workflow execution modes. Parallel complement also has
  its own concurrency setting.
- Temporal exposes overlap policies (Skip, BufferOne/All, Cancel/TerminateOther, AllowAll).
- Prefect limits active deployment runs independently and chooses ENQUEUE or CANCEL_NEW on collision.

The common pattern is to keep logical identity, overlap behavior, and resource limits independent.

## Follow-ups

- Rename `worker.perFlowExecThreads` and `worker.maxPerFlowExecThreads` to
  `worker.defaultConcurrentJobsPerRun` and `worker.maxConcurrentJobsPerRun`; update the bundled
  configuration, environment-variable overrides, and documentation together.
- Add explicit queue/discard overlap policies. Phase 1 rejects when capacity is full, matching the
  current behavior.
- Give flow-less/ad-hoc job runs their own persisted logical time before applying the same policy at
  `job_id` scope.
- Make admission atomic across scheduler nodes. The current check plus insert is protected only by
  the existing process-local flow lock; a database claim/lock is still required for strict cluster
  enforcement.
- Add reprocessing policy for a terminal run at the same logical time (`none`, `failed`, or
  `completed`) and model retries as attempts rather than changing `schedule_time`.
- Complete the timezone work in `TODO.md`; logical-time equality must use a consistently resolved
  schedule zone/instant across the cluster.
