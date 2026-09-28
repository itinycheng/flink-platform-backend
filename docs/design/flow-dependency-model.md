# Flow Dependency Model

> **Status:** Implemented on `feature/flow-depends-on`; the open items at the end remain.
>
> This records why flow-level `depends_on` ended up the shape it did. The feature has never
> reached `main`, so every decision below was taken with zero production data and zero frontend
> to stay compatible with — that freedom is not available again once it ships.
>
> This document describes the current latest-attempt evaluator. The proposed lifecycle, retry,
> user-operation, and future data-period model is in
> [logical-flow-runs-and-dependencies.md](logical-flow-runs-and-dependencies.md).

## Context

`DependencyService` parks a job flow run in `WAITING` until its upstream flows are satisfied.
It was written alongside, but separate from, the pre-existing `DEPENDENT` job type
(`DependentCommandBuilder` + `DependentJob.DependentItem`), which stays untouched for backward
compatibility. The two look alike and are diverging; see [Two implementations](#two-implementations).

The decisions below also anticipate a stated direction for the platform: **weaken the workflow
as a unit and make the individual job first-class, with a job flow eventually containing exactly
one job**. Under that model flow-level and job-level dependency collapse into the same thing,
which is what made decision 1 safe.

## Decisions

### 1. Dependencies target a flow, never a job inside it

`Dependency.jobId` is removed. A dependency names a `flowId` and nothing finer.

Airflow (`ExternalTaskSensor` with `external_task_id`) and DolphinScheduler (`DEPENDENT_ON_TASK`)
both support task-level dependency, so this is not an industry default — it was dropped because
*this* implementation of it was unsound:

- The lookup was `selectLatestRunPerJob(null, jobIds)` — the job's globally latest run, across
  all flow runs. Airflow anchors to `logical_date` + `execution_delta`; DolphinScheduler to a
  date window. There was no anchor here, so with an upstream that runs several times a day,
  *which* flow run the matched job run belonged to was undefined.
- A job run carries no cron, so the schedule window needed a second round trip to fetch the
  containing flow run's cron. All of `UPSTREAM_SCHEDULE`'s complexity came from this.
- It punched through the flow's encapsulation: splitting or renaming an upstream job silently
  broke downstreams with nothing able to detect it.

Reinstating it later costs a field, an evaluator branch and a UI control, provided
`Dependency` stays an object (not flattened to `List<Long>`), `evaluateOne` stays per-item, and
`latestFlowRunId` survives.

### 2. `latestResolved` belongs to the config, not the item

The guard that catches a missed `populateLatestExecutions` call lives on `DependencyConfig`, and
`populateLatestExecutions` sets it **unconditionally** for every config it is handed.

On `Dependency` the flag meant two things at once: items with a null `flowId` were filtered out
of the prefetch and so were never marked, making `latestResolved == false` mean both "populate
never ran" and "this item needed no lookup". It only worked because `evaluateOne`'s null-`flowId`
early return happened to precede the guard.

Without the guard at all, a missed call reads as every item pending, and the run fails at its
deadline blaming a slow upstream — a programming error disguised as an operational one.

### 3. The schedule window reads the flow's live cron, not the run's snapshot

`selectLatestRunPerFlow` joins `t_job_flow` and returns `f.cron_expr` as
`LatestFlowRun.flowCronExpr`. Still one round trip.

Judging against the cron snapshotted onto the run (`JobFlowRunner` copies it at creation) means a
schedule change leaves a transition window where the decision is based on a schedule nobody
honours any more:

| Change | Snapshot behaviour | Live behaviour |
|---|---|---|
| daily → hourly | Last run carries `daily`; downstream treats it as current for up to 24h — **silently accepts stale data** | Next fire already due → PENDING, correctly waits for the hourly run |
| hourly → daily | Last run carries `hourly`; next fire long past → PENDING for a whole new period, often past the downstream's own deadline | Next fire not due → SATISFIED |
| cron cleared | Cron parsing throws and obscures the configuration error | Detected → `MISCONFIGURED` (decision 4) |

`Dependency.latestCronExpr` was renamed to `flowCronExpr` because it is no longer part of the
`latest*` group resolved from the run.

**`FlowScheduleService.resolveWaitDeadline` deliberately reads the run's snapshotted cron and
schedule time.** A deadline is a contract fixed when the run was created; editing the downstream's
own cron should not make a run that is already waiting time out early. The asymmetry is intentional.

### 4. An item no outcome could satisfy fails immediately

`DependencyState.MISCONFIGURED`, raised when the item as configured could not be satisfied by any
upstream outcome. `DependencyService.isWellConfigured` holds every such case, so none reaches
`isLatestRunWithinWindow` and escapes as an NPE or `IllegalStateException`. The dispatcher fails
the run as invalid dependency configuration; the offending item goes to `log.warn`.

Two sources:

| Source | Cases |
|---|---|
| **Expiry unusable** | no policy at all; `UPSTREAM_SCHEDULE` whose upstream cron is missing or unparsable; `FIXED_DURATION` whose `duration` is missing or unparsable |
| **No accepted status** | a `*_MATCHED` condition with an empty `statuses` — it accepts nothing, so no run could ever match |

The second was added later. It had been handled as "simply never met", which is *true* but lands
in the wrong outcome: with the default `onUnmatched = WAIT` the group stays PENDING until its
deadline. Ruling it out here also lets `evaluateOne` read `getStatuses()` without a null check.
Note `*_DONE` ignores `statuses` entirely, so an empty list is legitimate there.

Three situations were previously collapsed into one outcome:

| Situation | Nature | Outcome |
|---|---|---|
| Upstream still running | runtime wait | PENDING |
| Upstream settled on a rejected status | result mismatch | per `onUnmatched` |
| **Item that no outcome could satisfy** | **broken configuration** | **fail at once** |

Waiting on the third is pure waste — no further run is coming — and it surfaced as
"Waiting timeout, dependencies not satisfied", pointing the investigation at the upstream's speed
instead of at the config.

The check short-circuits **before** `precondition` and ignores `onUnmatched`, so a matching
sibling cannot mask it under `ANY_MATCHED`. `onUnmatched` is a policy over *results*; an item
that produced no result is not one. Locked by
`DependencyServiceTest#misconfiguredIsNotMaskedByAMatchingSibling`. **Reversible** if masking
turns out to be wanted: drop the short-circuit and let a `MISCONFIGURED` item count as one that
simply never meets the precondition.

Note this is a *runtime* backstop. The frontend should also check at configuration time
(see [Open items](#open-items)), but that cannot stop an upstream dropping its cron afterwards.

### 5. A new `ExpiryPolicy` enum, not a rename of `DependentStrategy`

```java
public enum ExpiryPolicy { UPSTREAM_SCHEDULE, FIXED_DURATION, NEVER }
```

`Dependency.strategy` → `Dependency.expiry`; `DependentStrategy` is left alone for the
`DEPENDENT` job type.

Reusing the shared enum — renaming its values under `@JsonAlias`, as `ExecutionCondition` did for
`AND`/`OR` — was rejected because **adding `NEVER` to it breaks the old job type**:
`DependentCommandBuilder`'s switch ends in `default: throw new RuntimeException(...)`, and its
config UI renders whatever the enum contains. Avoiding that would mean writing validation on the
old side purely to un-share the shared enum.

The deeper reason is that the two are diverging anyway — after decision 3 the same
`UPSTREAM_SCHEDULE` would compute a different window on each side. Same name, different meaning
is worse than two names.

`@JsonAlias` was the right tool for `AND` → `ALL_MATCHED`: one concept, new name. Here it is two
concepts sharing one name, which aliasing cannot fix. (Worth remembering that `@JsonAlias` only
covers *de*serialization — rewritten rows come back with the new name, so a code rollback can no
longer read them.)

### 6. Why three values, and why `NEVER` exists

An earlier plan was to keep only `UPSTREAM_SCHEDULE`, on the grounds that streaming upstreams
need no window and batch upstreams must have a cron. Both halves held up; the conclusion did not.

- **Streaming** is orthogonal to the status gate but not to the window. Under `*_MATCHED`,
  `evaluateOne` compares against the listed `statuses`, so a `RUNNING` upstream is SATISFIED once
  the user lists `RUNNING`; only `*_DONE` insists on a terminal state. What streaming does run
  into is the expiry window, which is evaluated first and measures a stream's `createTime` — its
  start, possibly weeks ago. `NEVER` is the only policy that lets a stream through. Still open.
- **"Batch must have a cron"** is not enforced today: `JobFlowController` requires a cron only
  when *scheduling* a `JOB_FLOW`; `runOnce` never checks, and `JOB_LIST` never does. Scheduleless
  upstreams legitimately exist.
- **One-shot upstreams** need "the last run counts however old it is" — an absence of window, not
  a window. That is `NEVER`, and it would have had to be invented anyway.

`NEVER` gives up the protection the other two provide, and this is the point worth understanding:
**the window does not guard against upstream failure — status matching already does. It guards
against an upstream that silently stopped being scheduled.** "The latest run succeeded" stays
true forever once an upstream is unscheduled; only the window can tell "just ran" from "ran three
weeks ago and never again". `NEVER` should carry that warning in the UI.

`FIXED_DURATION` earns its place where the downstream's tolerance differs from the upstream's
period — a five-minute upstream feeding an hourly downstream would go PENDING on any jitter under
`UPSTREAM_SCHEDULE`, because that window is exactly one upstream period.

A rejected middle road: keep only `UPSTREAM_SCHEDULE` and repurpose `duration` as a grace period
added to the cron window. Cheap to build (validation currently forbids `duration` there, so no
data would have conflicted) but harder to explain — the user must grasp an automatic window plus
an offset on top, where "ran within the last hour" is a single idea.

### 7. A null policy field means "unset", and is normalised in the setter

`precondition` and `onUnmatched` both have a default (`ALL_MATCHED`, `WAIT`), and both are read
by code that cannot take a null: `DependencyService.evaluate` switches on `precondition` over
an exhaustive set of enum constants, so a null there is an NPE, not a fallthrough.

A field initialiser alone only covers the *absent* key. Three other writers exist:

| Writer | Covered by field initialiser? |
|---|---|
| key absent (rows written before the field existed) | yes — the setter is never called |
| `{"precondition": null}` on the wire | no — Jackson calls the setter with null |
| `config.setPrecondition(null)` from Java | no |
| a `@JsonCreator` constructor, if one is ever added | no, and silently so |

`@JsonSetter(nulls = Nulls.SKIP)` buys the second row only. It is also the wrong layer: it makes
a Jackson annotation the load-bearing part of an invariant that non-wire callers must hold too,
and under a creator-based binding `SKIP` changes meaning to "the type's default" — null again —
so the guarantee would disappear with no compile error. Coalescing inside a hand-written setter
covers all four rows at once, and matches `JobFlowRequest.setConfig`, which already does this for
`ExecutionConfig`.

`@NullMarked` cannot replace the setters either, though it is worth having alongside them. Both
limits were measured, not assumed:

- NullAway runs with `-XepOpt:NullAway:OnlyNullMarked`, so it checks a call site only if *that*
  site is marked too. `DependencyService` is; `JobFlowRequest` and most of the codebase are
  not, and a `setPrecondition(null)` there compiles clean.
- Jackson drives the setters by reflection, which no compile-time checker sees at all. With
  `@NullMarked` on and the coalescing removed, `{"precondition":null}` still deserialises to
  null.

What `@NullMarked` does buy is the *reading* side: `getPrecondition()` and `getDependencies()`
are declared non-null, so `DependencyService`'s unguarded `switch (precondition)` and
`getDependencies().stream()` are statically justified rather than true by convention. The
annotation states the invariant and checks the marked callers; the setters are what actually
hold it. Hence both, and hence the field initialisers as well — those cover the one case a
setter cannot, an absent key, where the setter is never called.

Treating null as *unset* rather than rejecting it is the deliberate half of this. The alternative
— validating `precondition != null` in `JobFlowRequest.verifyDependsOn` — cannot be combined with
a default: once the setter defaults, the check is unreachable, and the two together read as a
required field that is quietly optional. "Omitted means default" is also the only reading a front
end can honour, since it has no way to distinguish an unset `<select>` from a missing key. That
validation branch was therefore removed rather than kept alongside.

### 8. Naming

Mapper methods mirror SQL (`select*`); service methods state their contract
(`get*` non-null, `find*` nullable, `list*`, `map*` with the key in the name). Written up in
CLAUDE.md under *DAO Naming Conventions*, and applied here: `selectLatestRunPerFlow`,
`mapLatestRunByFlowId`, `listLatestLiteRunPerJob`.

`isFreshEnough` → `isLatestRunWithinWindow`, which flips all three branches positive and removes the
`!x.isBefore(y)` double negatives.

`selectLatestRunPerFlow` returns a `JobFlowRunDetails` view rather than `JobFlowRun`, so the joined
`f.cron_expr` cannot masquerade as the run's own `cron_expr` column.

The enums stay in `common/enums` by preference, though `UnmatchedPolicy` and `ExpiryPolicy` are
used only by the dependency code and would sit more accurately beside it. `ExecutionCondition`
genuinely belongs there — `JobVertex` and `ConditionJob` share it.

## Two implementations

| | `DEPENDENT` job type (shipped) | Flow-level `depends_on` (this work) |
|---|---|---|
| Config | `DependentJob.DependentItem` | `Dependency` |
| Evaluation | `DependentCommandBuilder` | `DependencyService` |
| Strategy enum | `DependentStrategy` | `ExpiryPolicy` |
| Cron source | run snapshot | **flow definition, live** |
| Combination | AND / OR | four-way `ExecutionCondition` |
| Result | boolean | four-valued `DependencyState` |
| Target granularity | flow *or* job | flow only |
| No cron | `RuntimeException` | `MISCONFIGURED` → fail + alert |

The duplication is real. It is left in place because the old path is shipped and the new one is
not, and because the old one is expected to be retired by the new one under the "job as
first-class citizen" direction, at which point the duplication disappears rather than needing a
merge.

## Deliberate: dependencies cross workspaces

Gating on a flow in another workspace is the point — chaining work across teams is what flow-level
dependencies are for. Two things follow that are easy to trip over:

- **It works today by accident, not by contract.** `WorkspaceTenantLineHandler.ignoreTable` returns
  `true` when `RequestContext.getWorkspaceId()` is null, and evaluation runs on the `@Scheduled`
  thread, where it always is. Give the scheduler a workspace context and cross-team chaining breaks
  silently. Anyone touching that handler needs to know this read is meant to be unscoped.
- **Target existence is validated cross-workspace.** `verifyDependsOn` resolves every referenced
  flow id through an explicitly tenant-unscoped lookup, so a typo is rejected before the run can
  wait. Whether a cross-workspace dependency needs the target team's consent remains an open
  product question.

## Open items

1. **Streaming upstreams work only under one narrow configuration.** The status gate is already
   fine: `ALL_MATCHED` / `ANY_MATCHED` with `RUNNING` in `statuses` admits a live stream. The
   window is not. It is checked before the status gate and measures the upstream's `createTime`,
   which for a stream is its start — so `UPSTREAM_SCHEDULE` (a stream usually has no cron, giving
   MISCONFIGURED) and `FIXED_DURATION` (reads the timestamp backwards, rewarding a job that just
   restarted over one that has run stably for a week) both fail it, leaving `NEVER` as the only
   workable choice and costing the stopped-being-scheduled protection that the window exists for.
   Needs a window that means "still alive" rather than "started recently". Deciding what
   "depending on a stream" means is a prerequisite: process liveness alone does not imply the
   data is written.
2. **"Latest run" means latest *attempt*, and the UI must say so.** `selectLatestRunPerFlow` picks
   `max(id)` per flow regardless of status, so `FIXED_DURATION = 6h` + `statuses = [SUCCESS]` means
   "the most recent attempt was within 6h **and** succeeded", not "succeeded at some point in the
   last 6h". An upstream that succeeded at 10:00 and failed at 11:00 does **not** satisfy it. That
   is deliberate. Under the current implementation a manual run gets a higher id and becomes the
   latest, unblocking the downstream. The forward design separates **Retry failed** (same logical
   work) from **Run now** (new logical work), but this latest-attempt mode remains conservative:
   a label reading "succeeded within 6 hours" would be a lie. Say "most recent run succeeded".
3. **Configuration-time cron check.** When `UPSTREAM_SCHEDULE` is selected, the UI should verify
   the chosen upstream has a cron and say so immediately, rather than leaving decision 4 to catch
   it at runtime.
4. **UI labels.** `LAST_EXECUTION_*`-era names are internal identifiers; the three options should
   read as "跟随上游排期" / "最近 [1小时] 内跑过" / "一直有效（上游停止调度后下游不会察觉）".
5. **`UPSTREAM_SCHEDULE` window is exactly one upstream period.** No jitter tolerance. Only becomes a
   problem with upstreams substantially denser than their downstreams; `FIXED_DURATION` is the
   escape hatch until it does.
