# Alerting Strategy

> **Status:** Design — not yet implemented. Tracked in [TODO.md](../../TODO.md).
>
> Raised while implementing flow-level `depends_on`. The new `WAITING` status made one gap
> acute (a run can now stay silent for hours by design), and reviewing the surrounding code
> turned up three pre-existing issues worth fixing at the same time.

## Context

Flow-level prerequisite dependencies park a run in `WAITING` until its upstreams are
satisfied. Waiting is bounded — the upstream flow's next cron fire, or `worker.flowWaitTimeout`
(12h in the shipped profiles) when the flow has no cron — but *within* that window the run
produces no alert at all. A slow upstream therefore delays the downstream's failure signal by
up to the whole window.

That is the trigger for this document, but problems 2–4 below predate `depends_on` and affect
every alert the platform sends.

## Current design

`AlertSendingService` is the single exit point. Everything funnels through one method:

```java
// flink-platform-alert/src/main/java/com/flink/platform/alert/AlertSendingService.java
private void dispatch(JobFlowRun jobFlowRun, String alertMsg, boolean bypassFilter) {
    var alerts = jobFlowRun.getAlerts();
    if (CollectionUtils.isEmpty(alerts)) {
        return;
    }

    var finalStatus = jobFlowRun.getStatus();
    alerts.stream()
            .filter(alert -> bypassFilter
                    || CollectionUtils.isEmpty(alert.getStatuses())
                    || alert.getStatuses().contains(finalStatus))
            .forEach(alert -> alertSender.sendAlert(alert.getAlertId(), jobFlowRun, alertMsg));
}
```

Two modes:

| Method | `bypassFilter` | Meaning |
|---|---|---|
| `sendAlerts(run[, msg])` | `false` | Honours each `AlertConfig.statuses`; an empty list means "all statuses" |
| `sendAlertsDirectly(run, msg)` | `true` | Unconditional — the user's status filter does not apply |
| `sendErrAlertsDirectly(flow, msg)` | `true` | Builds a throwaway run from the flow definition, forces `status = FAILURE`, then sends directly |

The filtered/direct split came from `feat(alert): terminal-only trigger statuses; split filtered
vs direct sending` on master.

### Call sites

| Location | Mode | Occasion |
|---|---|---|
| `FlowExecuteThread#completeAndNotify` | filtered | Flow reached a terminal status |
| `FlowExecuteThread#handleTimeout` | direct | Execution timeout, `ALARM` strategy |
| `FlowRunDispatcher#failWaitingFlowRun` | direct | A `WAITING` run gave up (dependency hopeless, or wait timed out) |
| `FlowRunDispatcher#submitToExecutor` | direct | Flow has no job vertex |
| `WorkerHeartbeat` | direct | Failover with no active worker |
| `JobFlowRunner` ×2 | `sendErrAlertsDirectly` | A previous run is still non-terminal, so this trigger is skipped |
| `JobsInJobListStatusChecker` | filtered | Cron health check |
| `UnscheduledJobFlowChecker` | `sendErrAlertsDirectly` | Cron health check |

Note that the direct mode dominates. Only terminal-status notifications and two cron checkers
respect the user's `AlertConfig.statuses`.

## Problems

### 1. A waiting run is silent until its deadline

A `WAITING` run emits nothing between creation and the moment it either advances or times out.
With `flowWaitTimeout` at 12h — or a daily upstream whose next fire is 24h away — an operator
learns that a downstream flow could not run only after the delay has already been absorbed.

There is a `TODO` at the exact spot, in `FlowRunDispatcher#advanceWaitingRuns`:

```java
case PENDING -> {
    // TODO: a WAITING run stays silent until the deadline, so a slow upstream can delay the
    //  alert by the whole window (flowWaitTimeout, or the next cron fire). Warn once partway
    //  through the wait instead, and say which dependency is still outstanding.
    if (waitTimedOut(waitingRun)) { ... }
}
```

**Why it matters more than it looks:** the run is not stuck or broken, so nothing else in the
system will notice it either. There is no failed status for a monitoring system to pick up, and
the flow list shows `WAITING`, which looks healthy.

### 2. Direct alerts have no throttling

`sendAlertsDirectly` sends on every call with no de-duplication. The clearest case is
`JobFlowRunner`'s duplicate-run guard: when an upstream stalls, **every cron tick** produces
another "There is already a waiting/running jobFlowRun" alert. An hourly flow sends one an
hour; a 5-minute flow sends twelve an hour. These bury the alerts that matter.

This also blocks problem 1: adding a mid-wait warning without throttling would just create a
second flood.

### 3. Alerts carry no diagnostic context

The wait-timeout alert reads:

```
Waiting timeout, dependencies not satisfied before next schedule
```

It does not say **which** dependency is outstanding, **what status** that upstream is in, or
**how long** the run waited. The recipient has to go look all of it up.

The data is already in hand: `DependencyService#populateLatestExecutionInfo` fills
`latestFlowRunId` / `latestJobRunId` / `latestStatus` / `latestCreateTime` onto each
`Dependency` during evaluation. `DependentCommandBuilder` (the job-level DEPENDENT path) already
does exactly this and builds a per-item message from those fields — the flow-level path just
throws the detail away.

**Implementation note:** `DependencyService#evaluate` currently returns only a `Verdict`, so
the per-item detail is discarded at the boundary. Exposing it needs either a richer return type
(verdict + the evaluated items) or a second method the caller invokes when composing the alert.

### 4. `sendErrAlertsDirectly` fabricates a `FAILURE` status

```java
public void sendErrAlertsDirectly(JobFlow jobFlow, String alertMsg) {
    var jobFlowRun = jobFlowService.copyToJobFlowRun(jobFlow);
    jobFlowRun.setStatus(FAILURE);   // this run does not exist and did not fail
    sendAlertsDirectly(jobFlowRun, alertMsg);
}
```

The run object is synthetic and no execution failed — the trigger was *skipped*. Any recipient
that routes or counts by status will treat it as a genuine flow failure. Because it also goes
out in direct mode, `AlertConfig.statuses` cannot filter it either.

## Proposed solutions

### For 1 — warn partway through the wait

Emit one warning when a run has been waiting past a threshold, then stay quiet until the
deadline.

Threshold options:

- **Fraction of the deadline** (e.g. 50%) — adapts to both hourly and daily flows, but for a
  5-minute flow the warning fires 2.5 minutes in, which is noise.
- **Fixed duration** (e.g. 1h) — sensible for daily flows, never fires for short-cycle ones
  whose deadline is shorter than the threshold.
- **`min(deadline × fraction, cap)`, configurable** — recommended; behaves for both ends.

Tracking "already warned" (the poller runs every 3s, so this is required):

- **In-memory `Set<Long>` per worker** — simplest; a worker restart re-sends one warning per
  waiting run. Acceptable, and failover already moves runs between workers.
- **A column on `t_job_flow_run`** — survives restarts, costs a migration and a write.

Recommend the in-memory set, documented as "at-least-once".

### For 2 — throttle the direct channel

De-duplicate by `(flowId, alert kind)` within a time window, inside `dispatch` so every caller
benefits. Important: **do not throttle terminal-status notifications** — a flow's success/failure
alert must always go out. Throttling should apply only to the repeating-by-nature ones
(duplicate-run guard, mid-wait warnings).

This implies alerts need a "kind" to throttle on, which `AlertSendingService` does not model
today. Introducing one is the bulk of the work here.

### For 3 — put the dependency state in the message

Follow `DependentCommandBuilder`'s shape, per unsatisfied item:

```
flowId: 100, jobId: -, latest run: 12345, status: RUNNING, waited: 2h13m
```

Cheapest of the four and it takes the edge off problem 1 even before the mid-wait warning
exists — when the timeout alert finally arrives, it at least says what to go look at.

### For 4 — stop forging a status

Options, in increasing order of disruption:

- Keep the synthetic run but leave `status` unset, and carry the reason in the message only.
- Give schedule-level notices their own type, separate from run outcomes.

Either way this changes what existing recipients see, so it wants its own rollout note.

## Sequencing

1. **3 (context in messages)** — small, self-contained, immediately useful.
2. **2 (throttling)** — needs the "alert kind" concept; is a prerequisite for 1.
3. **1 (mid-wait warning)** — the actual fix for the delayed-discovery concern.
4. **4 (fabricated status)** — semantic fix; schedule separately since it alters existing
   alert payloads.

1–3 belong together in one branch. 4 should be its own change with a compatibility note.

## Code pointers

| What | Where |
|---|---|
| Alert dispatch + the two modes | `flink-platform-alert/.../AlertSendingService.java` |
| Waiting poller, deadline, the `TODO` | `flink-platform-web/.../runner/FlowRunDispatcher.java` (`advanceWaitingRuns`, `hasWaitTimedOut`, `waitDeadline`, `failWaitingFlowRun`) |
| Dependency evaluation + `latest*` back-fill | `flink-platform-web/.../service/DependencyService.java` |
| Per-item message precedent (job-level DEPENDENT) | `flink-platform-web/.../command/dependent/DependentCommandBuilder.java` |
| Duplicate-run guard that floods | `flink-platform-web/.../quartz/JobFlowRunner.java` |
| Wait ceiling config | `WorkerConfig#flowWaitTimeout`, `application-{dev,docker}.yml` |
