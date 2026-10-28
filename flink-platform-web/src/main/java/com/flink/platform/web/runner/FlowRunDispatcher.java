package com.flink.platform.web.runner;

import com.flink.platform.alert.AlertSendingService;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.flowrun.FlowRunBizService;
import com.flink.platform.web.flowrun.FlowRunKillService;
import com.flink.platform.web.flowrun.FlowRunStateService;
import com.flink.platform.web.lifecycle.AppRunner;
import com.flink.platform.web.service.DependencyService;
import com.flink.platform.web.util.ThreadUtil;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import static com.flink.platform.common.constants.Constant.SEMICOLON;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.KILLING;
import static com.flink.platform.common.util.DurationUtil.humanizeDuration;
import static com.flink.platform.common.util.ExceptionUtil.runWithErrorLogging;
import static com.flink.platform.web.flowrun.FlowRunTransition.Outcome.APPLIED;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.joining;

/** Schedule job flow. */
@Slf4j
@Component
public class FlowRunDispatcher {

    private final WorkerConfig workerConfig;

    private final JobFlowRunService jobFlowRunService;

    private final FlowRunBizService flowRunBizService;

    private final AlertSendingService alertSendingService;

    private final FlowRunKillService killService;

    private final DependencyService dependencyService;

    private final FlowRunStateService stateService;

    private final ThreadPoolExecutor flowExecService;

    private final ExecutorService cleanupExecutor;

    private final Map<Long, JobFlowRun> inFlightFlowRuns = new ConcurrentHashMap<>();

    private final Set<Long> warnedWaitingRuns = ConcurrentHashMap.newKeySet();

    private final Set<Long> inFlightCleanups = ConcurrentHashMap.newKeySet();

    @Autowired
    public FlowRunDispatcher(
            WorkerConfig workerConfig,
            JobFlowRunService jobFlowRunService,
            FlowRunBizService flowRunBizService,
            AlertSendingService alertSendingService,
            FlowRunKillService killService,
            DependencyService dependencyService,
            FlowRunStateService stateService) {
        this.workerConfig = workerConfig;
        this.jobFlowRunService = jobFlowRunService;
        this.flowRunBizService = flowRunBizService;
        this.alertSendingService = alertSendingService;
        this.killService = killService;
        this.dependencyService = dependencyService;
        this.stateService = stateService;
        this.flowExecService = ThreadUtil.newFixedVirtualThreadExecutor("flow-exec", workerConfig.getFlowExecThreads());
        this.cleanupExecutor = ThreadUtil.newVirtualThreadExecutor("flow-kill");
    }

    @Scheduled(fixedDelay = 2, timeUnit = SECONDS)
    public void drainAndExecute() {
        if (AppRunner.isStopped()) {
            return;
        }

        // calculate available slot.
        var freeSlots = workerConfig.getFlowExecThreads() - inFlightFlowRuns.size();
        // clean up flow runs with KILLING status in separate threads.
        runWithErrorLogging("Failed to scan flow runs awaiting kill cleanup", () -> jobFlowRunService
                .listKillingRunsOnHost(inFlightCleanups, Math.max(freeSlots, 10))
                .forEach(this::submitCleanup));

        if (freeSlots <= 0) {
            return;
        }

        // TODO: when a host owns many non-terminal runs, the following can get costly.
        var inFlightRuns = new HashSet<>(inFlightFlowRuns.keySet());
        runWithErrorLogging("Failed to drain job flow runs for execution", () -> jobFlowRunService
                .listExecutableRunsOnHost(inFlightRuns, freeSlots)
                .forEach(this::submitToExecutor));
    }

    @Scheduled(fixedDelay = 3, timeUnit = SECONDS)
    public void advanceWaitingRuns() {
        if (AppRunner.isStopped()) {
            return;
        }

        // TODO: Add a batch size, prevent too many results ?
        var waitingRuns = jobFlowRunService.listWaitingRunsOnHost();
        if (CollectionUtils.isEmpty(waitingRuns)) {
            warnedWaitingRuns.clear();
            return;
        }
        warnedWaitingRuns.retainAll(waitingRuns.stream().map(JobFlowRun::getId).toList());

        var depConfigs = waitingRuns.stream()
                .map(JobFlowRun::getDependsOn)
                .filter(Objects::nonNull)
                .toList();
        dependencyService.populateLatestExecutions(depConfigs);

        var readyRuns = new ArrayList<JobFlowRun>();
        var failures = new ArrayList<WaitingFailure>();
        var warnings = new ArrayList<WaitingWarning>();
        for (var waiting : waitingRuns) {
            try {
                var deadline = flowRunBizService.resolveWaitDeadline(waiting);
                if (collectTimeoutFailure(waiting, deadline, failures)) {
                    continue;
                }

                switch (dependencyService.evaluate(waiting.getDependsOn())) {
                    case SATISFIED -> readyRuns.add(waiting);
                    case UNSATISFIABLE -> failures.add(new WaitingFailure(waiting, "Upstream status not accepted"));
                    case MISCONFIGURED -> failures.add(new WaitingFailure(waiting, "Invalid dependency configuration"));
                    case PENDING -> collectPendingActions(waiting, deadline, failures, warnings);
                }
            } catch (Exception e) {
                // TODO: persist exception to explain failed reason ?
                failures.add(new WaitingFailure(waiting, "Failed to evaluate dependency config"));
                log.error("Failed to advance waiting flow run: {}", waiting.getId(), e);
            }
        }

        advanceWaitingFlowRuns(readyRuns);
        failWaitingFlowRuns(failures);
        sendWaitingWarnings(warnings);
    }

    public void releaseInFlight(Long flowRunId) {
        inFlightFlowRuns.remove(flowRunId);
    }

    public List<JobFlowRun> getInFlightFlowRuns() {
        return new ArrayList<>(inFlightFlowRuns.values());
    }

    @PreDestroy
    public void close() {
        cleanupExecutor.shutdownNow();
        flowExecService.shutdownNow();
    }

    // ====================================================
    // ====================== private =====================
    // ====================================================

    private void submitCleanup(JobFlowRun run) {
        var id = run.getId();
        if (!inFlightCleanups.add(id)) {
            return;
        }

        try {
            cleanupExecutor.execute(() -> {
                try {
                    killService.advanceKilling(id);
                } catch (Exception e) {
                    log.error("Failed to reconcile killing flow run, flowRunId={}", id, e);
                } finally {
                    inFlightCleanups.remove(id);
                }
            });
        } catch (Exception e) {
            inFlightCleanups.remove(id);
            log.error("Failed to submit flow-run kill cleanup, flowRunId={}", id, e);
        }
    }

    private void collectPendingActions(
            JobFlowRun waiting, LocalDateTime deadline, List<WaitingFailure> failures, List<WaitingWarning> warnings) {
        if (collectTimeoutFailure(waiting, deadline, failures)) {
            return;
        }

        var now = LocalDateTime.now();
        var createTime = waiting.getCreateTime();
        var warningAt = createTime.plus(workerConfig.getFlowWaitWarningAfter());
        if (warningAt.isBefore(deadline) && !now.isBefore(warningAt) && warnedWaitingRuns.add(waiting.getId())) {
            warnings.add(new WaitingWarning(waiting, createTime, now, deadline));
        }
    }

    private boolean collectTimeoutFailure(JobFlowRun waiting, LocalDateTime deadline, List<WaitingFailure> failures) {
        if (!LocalDateTime.now().isAfter(deadline)) {
            return false;
        }

        failures.add(new WaitingFailure(waiting, "Waiting timeout, dependencies not satisfied by " + deadline));
        return true;
    }

    private void sendWaitingWarnings(List<WaitingWarning> warnings) {
        for (var warning : warnings) {
            var waiting = warning.run();
            try {
                var dependencies = waiting.getDependsOn().getDependencies().stream()
                        .map(dependency -> "flowId=%s, latestRunId=%s, status=%s"
                                .formatted(
                                        dependency.getFlowId(),
                                        dependency.getLatestFlowRunId(),
                                        dependency.getLatestStatus()))
                        .collect(joining(SEMICOLON));
                var waited = humanizeDuration(Duration.between(warning.startedAt(), warning.now()));
                var message = "Dependencies still pending after %s; deadline: %s; %s"
                        .formatted(waited, warning.deadline(), dependencies);
                alertSendingService.sendAlertsDirectly(waiting, message);
            } catch (Exception e) {
                warnedWaitingRuns.remove(waiting.getId());
                log.error("Failed to warn delayed waiting flow run: {}", waiting.getId(), e);
            }
        }
    }

    private void advanceWaitingFlowRuns(List<JobFlowRun> runs) {
        for (var run : runs) {
            var flowRunId = run.getId();
            try {
                if (stateService.submitWaiting(flowRunId).outcome() == APPLIED) {
                    log.info("All dependencies satisfied, advancing flow run {} WAITING -> SUBMITTED", flowRunId);
                }
            } catch (Exception e) {
                log.error("Failed to advance waiting flow run: {}", flowRunId, e);
            }
        }
    }

    private void failWaitingFlowRuns(List<WaitingFailure> failures) {
        for (var failure : failures) {
            try {
                var waiting = failure.run();
                var reason = failure.reason();
                // Conditional on WAITING: a concurrent kill must not be overwritten with FAILURE.
                if (stateService.failWaiting(waiting.getId()).outcome() != APPLIED) {
                    continue;
                }

                waiting.setStatus(FAILURE);
                waiting.setEndTime(LocalDateTime.now());
                log.warn("{}, failed waiting flow run: {}", reason, waiting.getId());
                alertSendingService.sendAlerts(waiting, reason);
            } catch (Exception e) {
                log.error("Failed to fail waiting flow run: {}", failure.run().getId(), e);
            }
        }
    }

    private void submitToExecutor(JobFlowRun jobFlowRun) {
        if (inFlightFlowRuns.putIfAbsent(jobFlowRun.getId(), jobFlowRun) != null) {
            log.warn("The JobFlowRun already managed, jobFlowRun: {}", jobFlowRun.getId());
            return;
        }

        var submitted = false;
        try {
            var status = jobFlowRun.getStatus();
            if (KILLING.equals(status)) {
                // unreachable.
                log.info("Flow run {} is {}, finalizing kill instead of executing", jobFlowRun.getId(), status);
                submitCleanup(jobFlowRun);
                return;
            }

            var flow = jobFlowRun.getFlow();
            if (flow == null || CollectionUtils.isEmpty(flow.getVertices())) {
                log.warn("No JobVertex found, no scheduling required, flow run id: {}", jobFlowRun.getId());
                failRunWithoutVertices(jobFlowRun);
                return;
            }

            log.info("Submitting workflow to executor, flowRunId: {}", jobFlowRun.getId());
            flowExecService.execute(new FlowExecuteThread(jobFlowRun, workerConfig));
            submitted = true;
        } catch (Exception e) {
            log.error("Failed to submit workflow to executor, flowRunId: {}", jobFlowRun.getId(), e);
        } finally {
            if (!submitted) {
                releaseInFlight(jobFlowRun.getId());
            }
        }
    }

    private void failRunWithoutVertices(JobFlowRun jobFlowRun) {
        // Only SUBMITTED is expected; a RUNNING row with no vertices is legacy/corrupt data left
        // for manual reconciliation. The conditional update protects a concurrent kill.
        if (stateService.failSubmitted(jobFlowRun.getId()).outcome() != APPLIED) {
            return;
        }
        jobFlowRun.setStatus(FAILURE);
        jobFlowRun.setEndTime(LocalDateTime.now());
        alertSendingService.sendAlerts(jobFlowRun, "No job vertex found");
    }

    private record WaitingFailure(JobFlowRun run, String reason) {}

    private record WaitingWarning(JobFlowRun run, LocalDateTime startedAt, LocalDateTime now, LocalDateTime deadline) {}
}
