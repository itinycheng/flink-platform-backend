package com.flink.platform.web.runner;

import com.flink.platform.alert.AlertSendingService;
import com.flink.platform.common.util.ExceptionUtil;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.lifecycle.AppRunner;
import com.flink.platform.web.service.DependencyService;
import com.flink.platform.web.service.KillJobService;
import com.flink.platform.web.service.QuartzService;
import com.flink.platform.web.util.ThreadUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.KILLING;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static java.util.concurrent.TimeUnit.SECONDS;

/** Schedule job flow. */
@Slf4j
@Component
public class FlowRunDispatcher {

    private final WorkerConfig workerConfig;

    private final JobFlowRunService jobFlowRunService;

    private final AlertSendingService alertSendingService;

    private final KillJobService killJobService;

    private final DependencyService dependencyService;

    private final QuartzService quartzService;

    private final ThreadPoolExecutor flowExecService;

    private final Map<Long, JobFlowRun> inFlightFlowRuns = new ConcurrentHashMap<>();

    @Autowired
    public FlowRunDispatcher(
            WorkerConfig workerConfig,
            JobFlowRunService jobFlowRunService,
            AlertSendingService alertSendingService,
            KillJobService killJobService,
            DependencyService dependencyService,
            QuartzService quartzService) {
        this.workerConfig = workerConfig;
        this.jobFlowRunService = jobFlowRunService;
        this.alertSendingService = alertSendingService;
        this.killJobService = killJobService;
        this.dependencyService = dependencyService;
        this.quartzService = quartzService;
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
            return;
        }

        var depConfigs = waitingRuns.stream()
                .map(JobFlowRun::getDependsOn)
                .filter(Objects::nonNull)
                .toList();
        dependencyService.populateLatestExecutions(depConfigs);

        for (var waitingRun : waitingRuns) {
            try {
                switch (dependencyService.evaluate(waitingRun.getDependsOn())) {
                    case SATISFIED -> advanceWaitingFlowRun(waitingRun);
                    case UNSATISFIABLE ->
                        failWaitingFlowRun(waitingRun, "Upstream settled on a status this flowRun doesn't accept");
                    case MISCONFIGURED ->
                        failWaitingFlowRun(waitingRun, "A dependency configuration is missing or invalid. ");
                    case PENDING -> failIfWaitTimedOut(waitingRun);
                }
            } catch (Exception e) {
                // TODO: persist exception to explain failed reason ?
                failWaitingFlowRun(waitingRun, "Failed to evaluate dependency config");
                log.error("Failed to advance waiting flow run: {}", waitingRun.getId(), e);
            }
        }
    }

    private void failIfWaitTimedOut(JobFlowRun waitingRun) {
        // TODO: a WAITING run stays silent until the deadline, so a slow upstream can delay the
        //  alert by the whole window (flowWaitTimeout, or the next cron fire). Warn once partway
        //  through the wait instead, and say which dependency is still outstanding.
        var deadline = calcWaitDeadline(waitingRun, waitingRun.getScheduleTime());
        if (LocalDateTime.now().isAfter(deadline)) {
            failWaitingFlowRun(waitingRun, "Waiting timeout, dependencies not satisfied by " + deadline);
        }
    }

    LocalDateTime calcWaitDeadline(JobFlowRun jobFlowRun, LocalDateTime scheduleTime) {
        var cronExpr = jobFlowRun.getCronExpr();
        if (StringUtils.isNotBlank(cronExpr)) {
            var nextFire = quartzService.nextTriggerTimeAfter(cronExpr, scheduleTime);
            if (!LocalDateTime.MAX.equals(nextFire)) {
                return nextFire;
            }
        }
        return scheduleTime.plus(workerConfig.getFlowWaitTimeout());
    }

    private void advanceWaitingFlowRun(JobFlowRun jobFlowRun) {
        if (jobFlowRunService.updateStatusFromWaiting(jobFlowRun.getId(), SUBMITTED)) {
            log.info("All dependencies satisfied, advancing flow run {} WAITING -> SUBMITTED", jobFlowRun.getId());
        }
    }

    private void failWaitingFlowRun(JobFlowRun waitingRun, String reason) {
        // Conditional on WAITING: a concurrent kill must not be overwritten with FAILURE.
        if (!jobFlowRunService.updateStatusFromWaiting(waitingRun.getId(), FAILURE)) {
            return;
        }

        waitingRun.setStatus(FAILURE);
        waitingRun.setEndTime(LocalDateTime.now());
        log.warn("{}, failed waiting flow run: {}", reason, waitingRun.getId());
        alertSendingService.sendAlerts(waitingRun, reason);
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

    public void releaseInFlight(Long flowRunId) {
        inFlightFlowRuns.remove(flowRunId);
    }

    public List<JobFlowRun> getInFlightFlowRuns() {
        return new ArrayList<>(inFlightFlowRuns.values());
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
}
