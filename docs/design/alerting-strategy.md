# Alerting Strategy

> **Status:** Partially implemented. WAITING runs now emit one warning after
> `worker.flowWaitWarningAfter` (default 1h) when the deadline is later than that threshold.
> Remaining alert-governance work is tracked in [TODO.md](../../TODO.md).
>
> Raised while implementing flow-level `depends_on`. The new `WAITING` status exposed a
> delayed-visibility gap, and reviewing the surrounding code turned up three pre-existing
> issues worth fixing at the same time.

## Context

Flow-level prerequisite dependencies park a run in `WAITING` until its upstreams are
satisfied. Waiting is bounded by the downstream flow's next cron fire (currently with a ten-second
lead), or `worker.flowWaitTimeout` (12h in the shipped profile) when the flow has no cron. The
basic warning prevents that entire window from passing silently; richer user-defined policy is
still deferred.

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
| `FlowRunDispatcher#failWaitingFlowRuns` | filtered | A `WAITING` run gave up (dependency hopeless, or wait timed out) |
| `FlowRunDispatcher#sendWaitingWarnings` | direct | A `WAITING` run passed the configured warning delay |
| `FlowRunDispatcher#submitToExecutor` | filtered | Flow has no job vertex |
| `WorkerHeartbeat` | direct | Failover with no active worker |
| `JobFlowRunner` ×2 | `sendErrAlertsDirectly` | A previous run is still non-terminal, so this trigger is skipped |
| `JobsInJobListStatusChecker` | filtered | Cron health check |
| `UnscheduledJobFlowChecker` | `sendErrAlertsDirectly` | Cron health check |

Note that the direct mode dominates. Only terminal-status notifications and two cron checkers
respect the user's `AlertConfig.statuses`.

## Problems

### 1. A waiting run needs configurable alerting

A minimal safeguard is implemented: a `WAITING` run emits one warning after
`worker.flowWaitWarningAfter`, provided its deadline is later than that threshold. The warning is
deduplicated by run id in worker memory, so it can be repeated after a worker restart.

The remaining problem is user control: the threshold, destinations, repetition and escalation
should eventually be expressed as per-flow event rules rather than more scheduler-level settings.

### 2. Direct alerts have no throttling

`sendAlertsDirectly` sends on every call with no de-duplication. The clearest case is
`JobFlowRunner`'s duplicate-run guard: when an upstream stalls, **every cron tick** produces
another "There is already a waiting/running jobFlowRun" alert. An hourly flow sends one an
hour; a 5-minute flow sends twelve an hour. These bury the alerts that matter.

The minimal mid-wait warning has its own run-level one-shot guard, but other direct alerts still
need event-aware throttling.

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

### For 1 — move the basic warning into user policy

The fixed-delay warning is intentionally a small first step. Future per-flow rules can replace it
with event-specific thresholds, routing, repetition and escalation without changing the lifecycle
deadline that transitions a run to `FAILURE`. Persistent event/rule/run idempotency can then
replace the current in-memory, at-least-once guard.

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

The minimal fixed-delay warning is implemented. Remaining work should proceed as:

1. **3 (structured context)** — distinguish the actual reason each dependency blocks.
2. **2 (event-aware throttling)** — introduce event kinds and central noise control.
3. **Per-flow rules** — move timing, routing and escalation to user configuration.
4. **4 (fabricated status)** — semantic fix; schedule separately since it alters existing
   alert payloads.

## Code pointers

| What | Where |
|---|---|
| Alert dispatch + the two modes | `flink-platform-alert/.../AlertSendingService.java` |
| Waiting poller, warning and deadline | `flink-platform-web/.../runner/FlowRunDispatcher.java` (`advanceWaitingRuns`, `collectWaitingOutcome`, `sendWaitingWarnings`, `calcWaitDeadline`) |
| Dependency evaluation + `latest*` back-fill | `flink-platform-web/.../service/DependencyService.java` |
| Per-item message precedent (job-level DEPENDENT) | `flink-platform-web/.../command/dependent/DependentCommandBuilder.java` |
| Duplicate-run guard that floods | `flink-platform-web/.../quartz/JobFlowRunner.java` |
| Wait timing config | `WorkerConfig#flowWaitTimeout`, `WorkerConfig#flowWaitWarningAfter`, `application.yml` |
