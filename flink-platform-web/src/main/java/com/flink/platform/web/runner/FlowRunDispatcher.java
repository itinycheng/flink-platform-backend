package com.flink.platform.web.runner;

import com.flink.platform.alert.AlertSendingService;
import com.flink.platform.common.util.ExceptionUtil;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.lifecycle.AppRunner;
import com.flink.platform.web.service.KillJobService;
import com.flink.platform.web.util.ThreadUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.KILLING;
import static java.util.concurrent.TimeUnit.SECONDS;

/** Schedule job flow. */
@Slf4j
@Component
public class FlowRunDispatcher {

    private final WorkerConfig workerConfig;

    private final JobFlowRunService jobFlowRunService;

    private final AlertSendingService alertSendingService;

    private final KillJobService killJobService;

    private final ThreadPoolExecutor flowExecService;

    private final Map<Long, JobFlowRun> inFlightFlowRuns = new ConcurrentHashMap<>();

    @Autowired
    public FlowRunDispatcher(
            WorkerConfig workerConfig,
            JobFlowRunService jobFlowRunService,
            AlertSendingService alertSendingService,
            KillJobService killJobService) {
        this.workerConfig = workerConfig;
        this.jobFlowRunService = jobFlowRunService;
        this.alertSendingService = alertSendingService;
        this.killJobService = killJobService;
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
                alertSendingService.sendAlertsDirectly(jobFlowRun, "No job vertex found");
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
