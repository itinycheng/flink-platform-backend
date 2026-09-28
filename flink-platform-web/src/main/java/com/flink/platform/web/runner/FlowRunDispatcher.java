package com.flink.platform.web.runner;

import com.flink.platform.alert.AlertSendingService;
import com.flink.platform.common.util.ExceptionUtil;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.lifecycle.AppRunner;
import com.flink.platform.web.service.DependencyService;
import com.flink.platform.web.service.FlowScheduleService;
import com.flink.platform.web.service.KillJobService;
import com.flink.platform.web.util.ThreadUtil;
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
import java.util.concurrent.ThreadPoolExecutor;

import static com.flink.platform.common.constants.Constant.SEMICOLON;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.KILLING;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static com.flink.platform.common.enums.ExecutionStatus.WAITING;
import static com.flink.platform.common.util.DurationUtil.humanizeDuration;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.joining;

/** Schedule job flow. */
@Slf4j
@Component
public class FlowRunDispatcher {

    private final WorkerConfig workerConfig;

    private final JobFlowRunService jobFlowRunService;

    private final AlertSendingService alertSendingService;

    private final KillJobService killJobService;

    private final DependencyService dependencyService;

    private final FlowScheduleService flowScheduleService;

    private final ThreadPoolExecutor flowExecService;

    private final Map<Long, JobFlowRun> inFlightFlowRuns = new ConcurrentHashMap<>();

    private final Set<Long> warnedWaitingRuns = ConcurrentHashMap.newKeySet();

    @Autowired
    public FlowRunDispatcher(
            WorkerConfig workerConfig,
            JobFlowRunService jobFlowRunService,
            AlertSendingService alertSendingService,
            KillJobService killJobService,
            DependencyService dependencyService,
            FlowScheduleService flowScheduleService) {
        this.workerConfig = workerConfig;
        this.jobFlowRunService = jobFlowRunService;
        this.alertSendingService = alertSendingService;
        this.killJobService = killJobService;
        this.dependencyService = dependencyService;
        this.flowScheduleService = flowScheduleService;
        this.flowExecService =
                ThreadUtil.newFixedVirtualThreadExecutor("FlowExecThread", workerConfig.getFlowExecThreads());
    }

    @Scheduled(fixedDelay = 2, timeUnit = SECONDS)
    public void drainAndExecute() {
        if (AppRunner.isStopped()) {
            return;
        }

        var freeSlots = workerConfig.getFlowExecThreads() - inFlightFlowRuns.size();
        if (freeSlots <= 0) {
            return;
        }

        // TODO: when a host owns many non-terminal runs, the following can get costly.
        var inFlight = new HashSet<>(inFlightFlowRuns.keySet());
        ExceptionUtil.runWithErrorLogging("Failed to drain job flow runs for execution.", () -> jobFlowRunService
                .listExecutableRunsOnHost(inFlight, freeSlots)
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

        var readyIds = new ArrayList<Long>();
        var failures = new ArrayList<WaitingFailure>();
        var warnings = new ArrayList<WaitingWarning>();
        for (var waiting : waitingRuns) {
            try {
                var deadline = flowScheduleService.resolveWaitDeadline(waiting);
                if (collectTimeoutFailure(waiting, deadline, failures)) {
                    continue;
                }

                switch (dependencyService.evaluate(waiting.getDependsOn())) {
                    case SATISFIED -> readyIds.add(waiting.getId());
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

        advanceWaitingFlowRuns(readyIds);
        failWaitingFlowRuns(failures);
        sendWaitingWarnings(warnings);
    }

    public void releaseInFlight(Long flowRunId) {
        inFlightFlowRuns.remove(flowRunId);
    }

    public List<JobFlowRun> getInFlightFlowRuns() {
        return new ArrayList<>(inFlightFlowRuns.values());
    }

    // ====================================================
    // ====================== private =====================
    // ====================================================

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

    private void advanceWaitingFlowRuns(List<Long> flowRunIds) {
        if (flowRunIds.isEmpty()) {
            return;
        }

        try {
            var updated = jobFlowRunService.updateStatusFromTo(flowRunIds, WAITING, SUBMITTED);
            if (updated > 0) {
                log.info(
                        "All dependencies satisfied, advancing {} of {} flow runs WAITING -> SUBMITTED",
                        updated,
                        flowRunIds.size());
            }
        } catch (Exception e) {
            log.error("Failed to advance waiting flow runs", e);
        }
    }

    private void failWaitingFlowRuns(List<WaitingFailure> failures) {
        for (var failure : failures) {
            try {
                var waiting = failure.run();
                var reason = failure.reason();
                // Conditional on WAITING: a concurrent kill must not be overwritten with FAILURE.
                if (!jobFlowRunService.updateStatusFromTo(waiting.getId(), WAITING, FAILURE)) {
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
                log.info("Flow run {} is {}, finalizing kill instead of executing", jobFlowRun.getId(), status);
                killJobService.forceKillFlowRun(jobFlowRun.getId());
                return;
            }

            var flow = jobFlowRun.getFlow();
            if (flow == null || CollectionUtils.isEmpty(flow.getVertices())) {
                log.warn("No JobVertex found, no scheduling required, flow run id: {}", jobFlowRun.getId());
                failAndUpdateJobFlowRun(jobFlowRun);
                alertSendingService.sendAlerts(jobFlowRun, "No job vertex found");
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

    private void failAndUpdateJobFlowRun(JobFlowRun jobFlowRun) {
        var currentTime = LocalDateTime.now();
        var newJobFlowRun = new JobFlowRun();
        newJobFlowRun.setId(jobFlowRun.getId());
        newJobFlowRun.setStatus(FAILURE);
        if (jobFlowRun.getStartTime() == null) {
            newJobFlowRun.setStartTime(currentTime);
        }
        newJobFlowRun.setEndTime(currentTime);
        jobFlowRunService.updateById(newJobFlowRun);

        jobFlowRun.setStatus(FAILURE);
        jobFlowRun.setStartTime(currentTime);
        jobFlowRun.setEndTime(currentTime);
    }

    private record WaitingFailure(JobFlowRun run, String reason) {}

    private record WaitingWarning(JobFlowRun run, LocalDateTime startedAt, LocalDateTime now, LocalDateTime deadline) {}
}
