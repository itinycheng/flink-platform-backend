# Logical Flow Runs, Retry, and Dependency Matching

> **Status:** Partly implemented — transactional flow-run admission and the normal expired-WAITING
> replacement path are in the code. Losing the WAITING CAS still fails admission; resolving that
> race is deferred to the lifecycle state-machine work. A DB-backed two-worker concurrency test and
> Phases 2–4 also remain.
>
> This is the forward-looking lifecycle design for flow-level `depends_on`. The current evaluator
> and its compatibility decisions remain documented in
> [flow-dependency-model.md](flow-dependency-model.md); alert delivery remains in
> [alerting-strategy.md](alerting-strategy.md).
> The period-aware mode below is a future design. This document does not change the current
> latest-upstream-run behavior or the existing `DEPENDENT` job type.

## Why this design exists

The first `depends_on` implementation added a `WAITING` flow-run status and evaluated each
dependency against the upstream flow's latest attempt. That is enough to express "the latest
upstream run is recent and has an accepted status", but it exposed three different concerns that
must not be collapsed into one timestamp:

1. **Schedule occurrence.** Quartz intended a flow to run at a particular time, even if the JVM
   started it later. `JobFlowRun.scheduleTime` already records this anchor.
2. **Logical work.** A run may represent yesterday's increment, a weekly aggregate, or a full
   snapshot. Two flows processing the same data need not fire at the same clock time.
3. **Execution attempt.** Retrying failed logical work should not silently turn it into today's
   work merely because the retry happened today.

Using `(flowId, scheduleTime)` as a universal dependency key would therefore be too strict. A
daily incremental producer at 02:00 and its consumer at 04:00 have different schedule times but
may both represent the same business date. A full-snapshot producer may have no meaningful
partition at all: its consumer normally wants the latest usable snapshot within a freshness
limit.

The immediate lifecycle bug was narrower but came from the same missing separation. A scheduled
`WAITING` run uses the flow's next cron fire as its deadline. At exactly that fire, the old run can
still be non-terminal when the fresh occurrence is admitted. The admission service now replaces
that expired row when it wins the status CAS. A remaining race exists when the dispatcher changes
the row after admission reads it but before that CAS; the current implementation fails fast and
leaves the candidate uncreated.

## Goals

- Never lose a scheduled occurrence because the preceding `WAITING` run expires at that same
  occurrence.
- Keep `FAILURE` as the terminal status for a dependency wait timeout; do not add an `EXPIRED` or
  `SUPERSEDED` status.
- Give retry, run-now, and backfill distinct and unsurprising meanings.
- Preserve the scheduled anchor across retries without making raw timestamps part of the normal
  user experience.
- Support both snapshot/full-load dependencies and period/incremental dependencies without
  pretending one matching rule fits both.
- Keep overlap control separate from retry identity and dependency matching.
- Introduce the data-period model only when the product exposes a use case for it; do not delay the
  lost-cron-tick fix on that larger model.

## Non-goals

- Replacing Quartz or the existing DB-driven dispatcher.
- Adding a new execution status for a timed-out wait.
- Inferring whether arbitrary SQL, Shell, or Flink code is full-load or incremental.
- Building a complete asset catalog or a general partition algebra in the first implementation.
- Making a manual run repair a historical batch unless the user explicitly chose **Retry** or
  **Backfill**.
- Extending `JOB_LIST`. It is a legacy type planned for retirement: existing scheduling and
  overlap behaviour must keep working, but retry lineage, period matching, backfill, and other new
  lifecycle features target `JOB_FLOW` only.

## Terminology and invariants

### Schedule time

`scheduleTime` is the immutable time at which an occurrence was intended to fire. It is not the
wall-clock start time and is not, by itself, the data processed by the run.

- A delayed 10:00 cron occurrence that starts at 10:03 still has `scheduleTime = 10:00`.
- A retry inherits the original `scheduleTime`.
- **Run now** gets a new anchor derived from that trigger.
- A sub-flow already inherits its parent's resolved schedule time; that behaviour remains.

### Logical run and attempt

A **logical run** is one unit of intended work. An **attempt** is one execution of that work.

```text
logical run: orders-daily / business period 2026-09-27
  attempt 1: FAILURE
  attempt 2: SUCCESS
```

The first implementation may represent this with `retry_root_id`, `retry_of_id`, and
`attempt_no` on `t_job_flow_run` instead of creating a separate logical-run table:

- the first attempt has no `retry_root_id` and `attempt_no = 1`;
- a retry sets `retry_root_id` to the first attempt, `retry_of_id` to the attempt being retried,
  and increments `attempt_no` under a DB lock;
- `COALESCE(retry_root_id, id)` is the internal logical-run identity;
- every attempt remains a separate row, so history is not overwritten.

The exact columns are an implementation choice. The invariants are not:

1. only one attempt of a logical run may be active;
2. retries retain `scheduleTime`, the flow/config snapshot, dependency config, and business-time
   parameters unless an explicit override is recorded;
3. retry identity does not decide whether two different logical runs may overlap.

### Data period

A **data period** describes the data represented by the logical run. It may eventually be stored
as a partition key or `[data_interval_start, data_interval_end)`.

It is intentionally separate from `scheduleTime`:

```text
producer scheduleTime: 2026-09-28 02:00
consumer scheduleTime: 2026-09-28 04:00
both data period:       2026-09-27
```

Period metadata is required only for period-aware dependency matching and backfill. The existing
latest-attempt dependency mode does not need it.

### Boundary between admission and dependency matching

Admission compares `scheduleTime` only among active runs of the **same flow** to reject a duplicate
ordinary occurrence; `maxActiveRuns` separately limits overlap. Terminal history may be run again.
This does not choose which run of **another flow** satisfies a dependency. The current `depends_on`
lookup chooses that upstream flow's latest attempt, regardless of the downstream's `scheduleTime`.
That is intentional for snapshots, but an older incremental waiter can otherwise be released by a
newer, unrelated upstream period. The future period mode addresses that case without changing
same-flow admission or overloading raw timestamp equality with business meaning.

## User operations

The UI and API should expose business operations, not logical-date mechanics.

| Operation | User meaning | Internal behaviour |
|---|---|---|
| **Retry failed** | Try the same work again | New attempt in the same logical run; inherit `scheduleTime` and inputs |
| **Run now** | Start new ad-hoc work using current inputs | New logical run and a new anchor; does not repair an older period |
| **Backfill** | Reprocess selected business dates/range | One logical run per derived data period; retries remain inside each period |
| **Recover failed nodes** | Continue a failed workflow without repeating successful nodes | Same logical run; execution strategy controls the selected jobs |

The normal retry action must not ask the user for a timestamp. A backfill asks for a business date
or range, then derives the internal anchors. An advanced API may accept an explicit anchor, but it
must not be the primary UI.

## Decision 1: serialise admission and replace expired WAITING occurrences

This is the first, independent implementation phase. It requires neither attempt columns nor a
data-period model.

Route `JOB_FLOW` creation through one transactional Spring service:

1. lock the `t_job_flow` row (`SELECT ... FOR UPDATE`) so the decision is cluster-wide; the
   current `synchronized (code)` protects only one JVM;
2. load the flow's non-terminal runs, excluding a pre-created candidate row when applicable;
3. classify a `WAITING` run as expired when `resolveWaitDeadline(old) <=` the physical admission
   time;
4. apply duplicate/capacity policy to the remaining active runs; a `SUBMITTED`, `RUNNING`,
   `CREATED`, `KILLING`, or unexpired `WAITING` run may still block the candidate;
5. conditionally update every expired row from `WAITING` to `FAILURE`; if any CAS loses, throw and
   roll back instead of making a decision from a stale snapshot;
6. save the candidate in the same transaction;
7. publish timeout or trigger-skipped alerts only after the transaction returns.

`JOB_LIST` keeps its existing running-Job guard, but gets no dedicated FlowRun scheduling branch.
Do not add `JOB_LIST`-specific lifecycle semantics while implementing later phases.

Quartz's `scheduledFireTime` remains the candidate's logical `scheduleTime`. It is not the physical
wait clock: `resolveWaitDeadline` starts from the old run's `createTime`, and admission compares the
deadline with wall-clock time. Admission treats equality as expired; the periodic dispatcher keeps
its strict `now.isAfter(deadline)` check.

`JobFlowRunService.updateStatusFromTo(..., WAITING, ...)` provides the status compare-and-set. If
the trigger wins it, the dispatcher later updates zero rows and sends no duplicate timeout alert.
If the dispatcher wins before the admission query, admission observes the resulting state. If it
wins after that query but before the trigger's CAS, admission currently throws and the fresh
candidate is not created. The future lifecycle state machine must distinguish an advance, timeout,
and user kill before deciding whether to block or create the candidate.

Do not implement this by excluding every `WAITING` row from conflict detection: a not-yet-expired
manual or irregular occurrence must not create an unbounded pile of waiting runs.

## Decision 2: keep overlap policy separate

Different `scheduleTime` values do not automatically permit parallel execution. A 02:00 run may
still be using the same sink when the 03:00 occurrence arrives.

The current behaviour is effectively **SKIP while active** and should remain so during the first
phase. A future explicit flow-level policy may offer:

- `SKIP` — discard a new occurrence while older work is executing;
- `QUEUE` — persist the new occurrence and execute it later;
- `PARALLEL` — permit different logical runs concurrently.

This policy is evaluated across the flow (and the existing partial-execution configuration), not
only among attempts with the same `scheduleTime`.

## Decision 3: provide two dependency meanings

### Most recent attempt must qualify — implement and ship first

User label: **上游最近一次运行符合条件**.

This is the current model: inspect the upstream's most recent attempt, require its status to match,
and enforce `UPSTREAM_SCHEDULE`, `FIXED_DURATION`, or `NEVER` freshness. It suits full loads,
snapshots, one-shot producers, and flows whose data period is not modelled.

Keep the conservative "latest attempt" rule: a success followed by a failure does not silently
fall back to the older success. The UI must say "most recent run", not "a successful run within
the last N hours".

Retrying that latest logical run creates a newer attempt in the same logical scope. When it
succeeds, a waiting downstream can advance. **Run now** creates different work and is not
presented as repairing a historical dependency.

### Matching data period — opt in later

User label: **对应业务周期** (the simple same-period mapping can be labelled **同一数据周期**).

This is for incremental data. A downstream logical run targets one or more upstream logical data
periods, not an equal clock timestamp. Attempts inside the targeted upstream logical run are
eligible; unrelated newer periods are ignored.

#### Period definition and mapping

An opt-in `JOB_FLOW` period definition must specify a schedule zone, granularity (initially day or
hour), and an explicit calendar offset from `scheduleTime`. Resolve and persist the period on each
logical run at creation; preserve it across retries. For example, if both flows process yesterday,
the 02:00 producer and 04:00 consumer on 2026-09-28 each resolve to the business-day interval
`[2026-09-27, 2026-09-28)` in the configured zone. `scheduleTime` is the input to this rule, not
the cross-flow join key. A flow without a period definition cannot be used as a target of this
mode; do not infer its data period by inspecting arbitrary task parameters or SQL.

Map a downstream period to a **fixed set of target upstream period keys** when the downstream
logical run is created. A target key identifies the upstream flow and one resolved interval
`(flowId, intervalStart, intervalEnd)`. The first version supports:

- equal granularity: the same interval key;
- downstream daily, upstream hourly: every upstream hourly interval intersecting the downstream
  day (a calendar day can have 23 or 25 hours across a daylight-saving transition);
- an explicit previous-period offset, such as **上一周期**, applied before resolving targets.

Other granularity combinations and custom mappings require an explicit mapping rule; they must
fail validation rather than silently fall back to the upstream's latest run. Normalize persisted
interval boundaries to instants while retaining the schedule zone and period definition needed to
explain them to users.

#### Evaluation and history

Persist the resolved target keys on the downstream logical run even when no upstream run exists.
For each target key, evaluate the latest eligible attempt **within that key**, including a later
retry or an explicit backfill of the same period. Keep the current status condition and
`onUnmatched` policy; a newer failed attempt must not fall back to an older success for the same
target. If several upstream logical runs claim one key, the newest eligible attempt is decisive;
never use an attempt from another key. One dependency item mapped to several hourly targets
qualifies only when **every** target qualifies; `precondition` then combines the results of the
configured dependency items. A missing target remains pending until the configured wait deadline;
an upstream with an unacceptable terminal status follows `onUnmatched`, while malformed period
configuration fails validation or evaluation as misconfigured. The latest-attempt mode's `expiry`
controls freshness; it is not a second age limit on an explicitly selected historical period.
Period-aware waits still have a deadline.

When the whole dependency group becomes satisfied and the downstream advances, persist each
qualifying upstream attempt ID alongside its period key in the same state transition. Do not pin
individual successes while the downstream is still waiting for other targets: a newer failure for
that period must still be seen. A completed downstream decision is immutable for audit; a later
upstream run does not rewrite its match. A retry of the downstream retains its original target
keys and configuration snapshot, then re-evaluates against the eligible attempts in those same
periods. `Run now` starts new work with a new anchor and cannot repair a historical target by
accident; historical work uses Retry or Backfill with an explicit period.

Do not implement period matching until the system can represent the period and retry it. Adding
only `upstream.schedule_time <= downstream.schedule_time` creates ambiguous behaviour for manual
runs and mixes snapshots with increments.

### Optional event-driven relation

For simple chains, **上游成功后运行** can avoid cross-schedule matching entirely. The upstream
completion creates or releases a downstream logical run and passes its data period. This is a
future product option, not required by the current fix.

## Retry, manual run, and backfill edge cases

### A scheduled attempt fails, then the user retries it

The retry keeps the original logical run and `scheduleTime`. Period-aware downstreams continue to
watch that logical period. Latest-attempt downstreams see the new attempt as the latest attempt.

### A scheduled attempt fails, then the user chooses Run now

This creates a new logical run. It may become the latest attempt for latest-attempt dependencies,
but it does not satisfy a period-aware dependency for an older period unless the user selected
Backfill/Retry. The UI should say this explicitly beside **Run now**.

### A full snapshot is rebuilt manually

Use latest-attempt matching with a freshness policy. No artificial daily partition is required.

### An incremental day is backfilled

Backfill creates or retries the selected business-period logical run. Downstreams mapped to that
period can then continue or be explicitly backfilled. A run executed today may therefore still
represent yesterday; its actual start time changes, its logical period does not.

### Flow cron changes

Existing logical runs retain their snapshotted `scheduleTime`, dependency config, and period.
Future occurrences use the new cron. Editing a definition must not retroactively re-key waiting or
failed work.

### Scheduleless and streaming flows

Scheduleless/manual producers use latest-attempt matching unless the caller supplies an explicit
data period. Streaming dependencies remain latest/liveness-oriented; a stream's start time is not
a useful incremental period. The streaming limitations recorded in
[flow-dependency-model.md](flow-dependency-model.md#open-items) remain open.

## Product surface

The dependency editor should start with two cards, not enum names:

1. **Use the upstream's most recent run** — status selector plus freshness; recommended for full
   loads and snapshots.
2. **Use the corresponding business period** — recommended for incremental jobs; hidden until
   period support is implemented. Show the derived upstream period(s) in a preview, including the
   02:00 producer / 04:00 consumer case and any previous-period offset.

Every selection must show a concrete preview, for example:

```text
The 04:00 downstream run for business date 2026-09-27 waits for the
upstream's 2026-09-27 period, regardless of when that period is retried.
```

The run page groups attempts under one logical run and shows:

- business period (when present);
- planned schedule time;
- attempt number and actual start/end time;
- dependency target and the attempt that satisfied it.

This keeps deterministic internals without requiring users to understand `logical_date`,
`execution_delta`, or raw database timestamps.

## Industry reference points

- [Airflow Dag Runs](https://airflow.apache.org/docs/apache-airflow/stable/core-concepts/dag-run.html)
  separates logical date/data interval from actual execution. Task clear/retry stays on the
  scheduled date; catchup/backfill operates over intervals.
- [Airflow ExternalTaskSensor](https://airflow.apache.org/docs/apache-airflow-providers-standard/stable/_api/airflow/providers/standard/sensors/external_task/index.html)
  defaults to the same logical date and permits `execution_delta` or a mapping function. It is
  deterministic but exposes significant scheduling vocabulary.
- [DolphinScheduler Dependent](https://github.com/apache/dolphinscheduler/blob/3.2.2/docs/docs/en/guide/task/dependent.md)
  presents relative business windows such as yesterday/last week plus wait/fail policy. This is
  easier to explain but less precise than a first-class period model.
- [Prefect manual retry](https://docs.prefect.io/v3/how-to-guides/workflows/retry-flow-runs)
  keeps the flow-run ID and parameters and increments `run_count`; its scheduler primarily creates
  independent runs rather than imposing one global cross-flow time-matching rule.
- [Dagster partition dependencies](https://docs.dagster.io/guides/build/partitions-and-backfills/defining-dependencies-between-partitioned-assets)
  model the data directly: matching partitions align automatically, and a daily downstream can map
  to all intersecting hourly upstream partitions.

The design here takes Airflow's stable logical attempt semantics, DolphinScheduler's user-facing
business language, Prefect's separation between scheduling and execution, and Dagster's rule that
incremental dependencies should ultimately match data partitions rather than clock timestamps.

## Delivery plan

### Phase 1 — transactional admission (partly implemented, no lifecycle schema change)

- Implemented: move flow-run admission into a transactional service and lock the flow row.
- Implemented: fail expired `WAITING` rows and save the candidate in one transaction when every CAS
  succeeds.
- Implemented: make timeout alerting single-winner and post-commit.
- Remaining: model CAS-loss outcomes in the lifecycle state machine instead of failing all of them.
- Remaining: add a DB-backed multi-node admission test.

### Phase 2 — explicit retry scope

- Persist attempt lineage (`retry_root_id`/`retry_of_id`/`attempt_no`, or an equivalent model).
- Add a retry operation that inherits `scheduleTime`, snapshots, and inputs.
- Keep Run now as a new logical run.
- Group attempts in query responses/UI.
- Keep flow-wide overlap policy independent of attempt grouping.

### Phase 3 — make the current dependency mode understandable

- Label it as latest-attempt matching and retain the existing expiry policies.
- Surface the matched/upstanding attempt and wait duration in diagnostics.
- Validate incompatible cron/freshness configurations at save time.
- Finish the alerting work in [alerting-strategy.md](alerting-strategy.md).

### Phase 4 — period-aware dependency and backfill (deferred)

- Add persisted period definition, resolved run period, target keys and mapping rules.
- Add the opt-in **对应业务周期** dependency mode; keep latest-attempt matching as the default.
- Add Retry/Run now/Backfill operations described above.
- Resolve and persist dependency target keys and the satisfying attempt for each key.
- Add previews and period-based history views before exposing the mode broadly.

## Required tests

### Transactional admission

- expired `WAITING` at fire `T` becomes `FAILURE` and the `T` run is created;
- a lost `WAITING` CAS rolls back and leaves the candidate uncreated;
- trigger-first timeout CAS sends one alert; the dispatcher loser sends none;
- a `WAITING` run whose deadline is after `T` still blocks;
- `RUNNING`/`SUBMITTED` still block according to the overlap policy;
- invalid cron/evaluation failure does not leave the flow lock or transaction open.

Still required after the state-machine decision:

- dispatcher advance, dispatcher timeout, and user-kill races produce the explicitly chosen
  candidate outcome;
- two workers admitting the same occurrence create one run in a real database test.

### Retry scope

- retry inherits schedule time, snapshots, inputs, and logical root;
- concurrent retries allocate only one active attempt;
- Run now creates a new logical root;
- sub-flow retry preserves the parent's business-time anchor;
- retry history remains queryable without overwriting the prior attempt.

### Period-aware mode

- same-period retry satisfies a waiter even when executed much later;
- a newer unrelated period does not satisfy an older waiter;
- daily-to-hourly mapping requires every intersecting hour;
- producer and consumer with different fire times can match the same business day;
- same-flow duplicate admission still uses `scheduleTime`, independent of dependency mode;
- full-snapshot latest matching remains independent of partitions;
- cron edits do not re-key existing logical runs;
- manual runs without a period cannot accidentally satisfy a historical period.

## Code areas

| Concern | Current location |
|---|---|
| Flow scheduling, transactional run creation and wait deadline | `flink-platform-web/.../service/FlowScheduleService.java` |
| Quartz trigger adapter and legacy duplicate alert | `flink-platform-web/.../quartz/JobFlowRunner.java` |
| Scheduled/run-once Quartz creation | `flink-platform-web/.../quartz/QuartzService.java` |
| Waiting polling and deadline | `flink-platform-web/.../runner/FlowRunDispatcher.java` |
| Non-terminal lookup and WAITING CAS | `flink-platform-dao/.../service/JobFlowRunService.java` |
| Dependency lookup/evaluation | `DependencyService`, `JobFlowMapper#selectLatestRunPerFlow` |
| Manual Run now | `JobFlowController#runOnce` |
| Sub-flow schedule-time inheritance | `FlowCommandExecutor#runOnceInTransaction` |
| Schema | `docs/sql/schema.sql`, `docs/sql/migration.sql` |
