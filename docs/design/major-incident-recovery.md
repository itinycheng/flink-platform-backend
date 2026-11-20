# Major incident and manual schedule recovery

> **Status:** Deferred proposal for owner review, 2026-11-20. No runtime changes implemented.
> Builds on [short database outage recovery](short-database-outage-recovery.md). Its cluster
> switch, operator hold, and backfill workflow are not prerequisites for short-outage work.

## Why this needs a separate policy

A prolonged outage may cross a business date, expire dependency waits, and miss Quartz fires.
Persisted `JobFlowRun.scheduleTime` anchors `biz*` variables, but `cur*` and unanchored
`${time:...}` expressions use wall-clock time when each Job starts. A resumed DAG can mix
yesterday's business anchor with today's current-time values. The current cron trigger has
no explicit misfire instruction, and `JobFlowRunner` uses Quartz's `scheduledFireTime` unless
a manual override is supplied. Verify the actual recovery fire times before deployment.
The platform has a manual `scheduleTime` override, not a first-class batch backfill feature.

## Proposed recovery policy

Always reconcile persisted runs and already-launched external tasks first; observation is
different from starting another JobRun or DAG successor. Short outages may resume the same
owned runs automatically. A prolonged cluster-wide outage or a business-date boundary enters
an operator hold before new work starts. Existing external resources can still be observed.
Operators inspect affected FlowRuns and business periods, cancel runs that should not continue,
then release the hold once to resume the rest. Missing periods are backfilled or skipped
explicitly, at FlowRun/period level rather than one action per DAG job. A one-node partition
holds that node's work, not the healthy cluster.

Do not assign a terminal status solely because an outage was long. Measure the configurable
threshold from the outage, not from an old `scheduleTime`; legitimate long-running flows have
old anchors. A persisted last-successful worker heartbeat or external incident timestamp
must survive process restart. Date-boundary checks need a defined cluster schedule zone;
the [timezone work](../../TODO.md#cross-timezone-scheduling--per-job-timezone-layered-default)
remains open. Review expired `WAITING` dependencies before releasing the hold.

## One durable cluster switch

Use one dedicated database control row with `RUNNING` / `PAUSED`, change time, operator,
reason, and revision. `t_config` stores editable, purgeable Flink/Spark version settings and
is not a cluster control contract. A `SYSTEM_MANAGE` API changes the row and creates an audit
event. It is outside workspace tenant filtering; `X-Workspace-Id` cannot narrow it. Coordinate
the UI contract with the separate `runnelo-ui` project.

An operator can pause before maintenance. After a major cluster-wide outage, the first
recovery transaction persists `PAUSED` before any node resumes dispatch. Nodes start with a
closed local gate until they read this mode and refresh their FlowRun ownership. Nodes may
poll for mode changes; the database row is checked at durable admission and job reservation
so a stale poll cannot admit work after pause commits. Commands already past admission may
finish launching; the switch does not kill external tasks.

While `PAUSED`, stop new Quartz/manual FlowRuns, new JobRuns, and DAG successors. Keep status
observation and durable cancellation available. Ordinary `runOnce` is held to prevent bypass.
The operator cancels unwanted runs, presses **Resume** once, then submits chosen historical
periods separately. If MySQL is still down, the pause API cannot persist a decision; the
node-local fail-closed behavior from the short-outage design applies.

Keep each Quartz scheduler in standby while the gate is closed, including startup until the
recovery decision. Returning early from `JobFlowRunner` alone can consume an occurrence.
Proposed default: skip missed cron ticks, resume future ticks, and backfill selected periods
explicitly. Configure and verify Quartz misfire behavior before deployment. Automatic
catch-up, if later needed, should be an opt-in per-flow policy with capacity and period rules.

## Deferred implementation boundary

The feature requires a small schema migration for the control row, privileged pause/resume
API, recovery inventory, verified Quartz standby/misfire behavior, and FlowRun/JobRun admission
checks. No new persisted run status is required. Roll out after short-outage behavior is
reviewed, with a coordinated upgrade so every worker obeys the gate. An API and runbook can
precede a UI. Review restart during `PAUSED`, resume racing with a cron fire, external-task
completion under the hold, date boundaries, and repeated administrator requests.

References: [Airflow defaults catch-up off and offers explicit backfill](https://airflow.apache.org/docs/apache-airflow/stable/core-concepts/dag-run.html#catchup)
and [Dagster lets operators choose partitions to backfill](https://docs.dagster.io/guides/build/partitions-and-backfills/backfilling-data).
