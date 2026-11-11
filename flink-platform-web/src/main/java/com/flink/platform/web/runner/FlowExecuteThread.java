package com.flink.platform.web.runner;

import com.flink.platform.alert.AlertSendingService;
import com.flink.platform.common.enums.TimeoutStrategy;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.dao.entity.JobFlowDag;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.util.JobFlowDagHelper;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.flowrun.FlowRunKillService;
import com.flink.platform.web.flowrun.FlowRunStateService;
import com.flink.platform.web.lifecycle.AppRunner;
import com.flink.platform.web.util.ThreadUtil;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

import static com.flink.platform.common.constants.Constant.HOST_IP;
import static com.flink.platform.common.enums.ExecutionStatus.KILLED;
import static com.flink.platform.common.enums.ExecutionStatus.RUNNING;
import static com.flink.platform.web.flowrun.FlowRunTransition.Outcome.APPLIED;
import static com.flink.platform.web.util.ThreadUtil.FIVE_SECOND_MILLIS;

/** Process flow in a separate thread. */
@Slf4j
public class FlowExecuteThread implements Runnable {

    private static final ExecutorService jobExecService = ThreadUtil.newVirtualThreadExecutor("job-exec");

    private final JobFlowRunService jobFlowRunService = SpringContext.getBean(JobFlowRunService.class);

    private final AlertSendingService alertSendingService = SpringContext.getBean(AlertSendingService.class);

    private final FlowRunKillService killService = SpringContext.getBean(FlowRunKillService.class);

    private final FlowRunStateService stateService = SpringContext.getBean(FlowRunStateService.class);

    private final FlowRunCoordinator flowRunCoordinator = SpringContext.getBean(FlowRunCoordinator.class);

    private final Map<Long, CompletableFuture<Void>> runningJobs = new ConcurrentHashMap<>();

    private final JobFlowRun jobFlowRun;

    private final WorkerConfig workerConfig;

    private final Semaphore semaphore;

    private volatile boolean aborted = false;

    public FlowExecuteThread(JobFlowRun jobFlowRun, WorkerConfig workerConfig) {
        this.jobFlowRun = jobFlowRun;
        this.workerConfig = workerConfig;
        this.semaphore = new Semaphore(getMaxJobsPerRun());
    }

    @Override
    public void run() {
        try {
            execute();
        } catch (Exception e) {
            log.error("Flow run {} execution aborted", jobFlowRun.getId(), e);
        } finally {
            flowRunCoordinator.releaseInFlight(jobFlowRun.getId());
        }
    }

    private void execute() {
        if (!claimOrResumeExecution()) {
            return;
        }

        // Process job flow.
        log.info("Start scheduling job flow: {}", jobFlowRun.getId());
        var flow = jobFlowRun.getFlow();
        flow.setConfig(jobFlowRun.getConfig());
        flow.getBeginVertices().forEach(jobVertex -> execVertex(jobVertex, flow));

        // Wait until all vertices are executed.
        var timeout = jobFlowRun.getTimeout();
        var startTime = jobFlowRun.getStartTime();
        var timeoutHandled = false;
        while (flow.hasUnexecutedVertices()) {
            if (AppRunner.isStopped()) {
                return;
            }

            if (aborted) {
                log.warn("Flow run {} aborted locally, leaving it for the new owner / restart", jobFlowRun.getId());
                return;
            }

            if (flowRunCoordinator.isOwnedByAnotherWorker(jobFlowRun.getId())) {
                log.warn("Flow run {} owned by another worker, abandoning local execution", jobFlowRun.getId());
                return;
            }

            // handle timeout.
            if (!timeoutHandled && timeout != null && timeout.isSatisfied(startTime)) {
                handleTimeout(timeout.getStrategies());
                timeoutHandled = true;
            }

            ThreadUtil.safeSleep(FIVE_SECOND_MILLIS);
        }

        // Wait for all jobs to complete before finalizing the flow run.
        CompletableFuture.allOf(runningJobs.values().toArray(new CompletableFuture[0]))
                .thenAccept(unused -> completeFlowRun(flow))
                .join();
    }

    private boolean claimOrResumeExecution() {
        return switch (jobFlowRun.getStatus()) {
            case SUBMITTED -> claimSubmittedRun();
            case RUNNING -> canResumeRunning();
            default -> false;
        };
    }

    private boolean claimSubmittedRun() {
        if (stateService.startRunning(jobFlowRun.getId(), HOST_IP).outcome() != APPLIED) {
            return false;
        }
        jobFlowRun.setStatus(RUNNING);
        jobFlowRun.setStartTime(LocalDateTime.now());
        return true;
    }

    private void handleTimeout(TimeoutStrategy[] strategies) {
        for (var strategy : strategies) {
            switch (strategy) {
                case ALARM -> alertSendingService.sendAlertsDirectly(jobFlowRun, "execution timeout");
                // Timeout is run-wide; a former owner may request durable kill intent after handoff.
                case FAILURE -> killService.requestKill(jobFlowRun.getId());
            }
        }
    }

    /** Complete the flow run after its jobs finish. */
    private void completeFlowRun(JobFlowDag flow) {
        var finalStatus = JobFlowDagHelper.getFinalStatus(flow);
        // Durable kill confirmation belongs to kill orchestration.
        if (finalStatus == KILLED) {
            killService.requestKill(jobFlowRun.getId(), HOST_IP);
            return;
        }
        stateService.completeRunning(jobFlowRun.getId(), HOST_IP, finalStatus);
    }

    // ! synchronized won't unmount the virtual thread, and thus block both its carrier and the underlying OS thread.
    // ! This doesn't make an application incorrect, but it might hinder its scalability.
    private synchronized void execVertex(JobVertex jobVertex, JobFlowDag flow) {
        if (AppRunner.isStopped()) {
            return;
        }

        if (runningJobs.containsKey(jobVertex.getId())) {
            log.warn("JobVertex: {} already executed", jobVertex.getId());
            return;
        }

        var runnable = new SemaphoreSupplier(semaphore, new JobExecuteThread(jobFlowRun.getId(), jobVertex));
        var jobVertexFuture = CompletableFuture.supplyAsync(runnable, jobExecService)
                .thenAccept(response -> handleResponse(response, jobVertex, flow));
        runningJobs.put(jobVertex.getId(), jobVertexFuture);
    }

    private void handleResponse(JobResponse jobResponse, JobVertex jobVertex, JobFlowDag flow) {
        if (jobResponse == JobResponse.ABORTED) {
            aborted = true;
            return;
        }

        jobVertex.setJobRunId(jobResponse.jobRunId());
        jobVertex.setJobRunStatus(jobResponse.status());

        for (var nextVertex : flow.getNextVertices(jobVertex)) {
            if (flow.isPreconditionSatisfied(nextVertex)) {
                execVertex(nextVertex, flow);
            }
        }
    }

    private boolean canResumeRunning() {
        var flowRun = jobFlowRunService.getLiteById(jobFlowRun.getId());
        return flowRun != null && flowRun.getStatus() == RUNNING && HOST_IP.equals(flowRun.getHost());
    }

    private int getMaxJobsPerRun() {
        var config = jobFlowRun.getConfig();
        var max = config.getMaxJobsPerRun();
        return max > 0 ? max : workerConfig.getPerFlowExecThreads();
    }
}
