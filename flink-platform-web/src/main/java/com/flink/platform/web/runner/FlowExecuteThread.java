package com.flink.platform.web.runner;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.TimeoutStrategy;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.dao.entity.JobFlowDag;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.util.JobFlowDagHelper;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.config.AppRunner;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.service.AlertSendingService;
import com.flink.platform.web.service.KillJobService;
import com.flink.platform.web.util.ThreadUtil;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import static com.flink.platform.common.enums.ExecutionStatus.RUNNING;

/** Process flow in a separate thread. */
@Slf4j
public class FlowExecuteThread implements Runnable {

    private static final ExecutorService jobExecService = ThreadUtil.newVirtualThreadExecutor("JobExecuteThread");

    private final JobFlowRun jobFlowRun;

    private final WorkerConfig workerConfig;

    private final Semaphore semaphore;

    private final Map<Long, CompletableFuture<Void>> runningJobs = new ConcurrentHashMap<>();

    private final JobFlowRunService jobFlowRunService = SpringContext.getBean(JobFlowRunService.class);

    private final AlertSendingService alertSendingService = SpringContext.getBean(AlertSendingService.class);

    private final KillJobService killJobService = SpringContext.getBean(KillJobService.class);

    private volatile boolean isRunning = true;

    public FlowExecuteThread(@Nonnull JobFlowRun jobFlowRun, @Nonnull WorkerConfig workerConfig) {
        this.jobFlowRun = jobFlowRun;
        this.workerConfig = workerConfig;
        this.semaphore = new Semaphore(workerConfig.getPerFlowExecThreads());
    }

    @Override
    public void run() {
        // Update jobFlowRun.
        var newJobFlowRun = new JobFlowRun();
        newJobFlowRun.setId(jobFlowRun.getId());
        newJobFlowRun.setStatus(RUNNING);
        if (jobFlowRun.getStartTime() == null) {
            var startTime = LocalDateTime.now();
            jobFlowRun.setStartTime(startTime);
            newJobFlowRun.setStartTime(startTime);
        }
        jobFlowRunService.updateById(newJobFlowRun);
        jobFlowRun.setStatus(RUNNING);

        // Process job flow.
        var flow = jobFlowRun.getFlow();
        flow.setConfig(jobFlowRun.getConfig());
        flow.getBeginVertices().forEach(jobVertex -> execVertex(jobVertex, flow));

        // Wait until all vertices are executed.
        var timeout = jobFlowRun.getTimeout();
        var startTime = jobFlowRun.getStartTime();
        var timeoutHandled = false;
        while (isRunning && flow.hasUnExecutedVertices()) {
            if (AppRunner.isStopped()) {
                return;
            }

            // handle timeout.
            if (!timeoutHandled && timeout != null && timeout.isSatisfied(startTime)) {
                handleTimeout(timeout.getStrategies());
                timeoutHandled = true;
            }

            ThreadUtil.sleep(5000);
        }

        // Wait for all jobs complete.
        CompletableFuture.allOf(runningJobs.values().toArray(new CompletableFuture[0]))
                .thenAccept(unused -> completeAndNotify(flow));
    }

    private void handleTimeout(TimeoutStrategy[] strategies) {
        for (var strategy : strategies) {
            switch (strategy) {
                case ALARM -> alertSendingService.sendAlerts(jobFlowRun, "execution timeout");
                case FAILURE -> killJobService.killRemoteFlow(jobFlowRun.getUserId(), jobFlowRun.getId());
            }
        }
    }

    /** Update status of jobFlow and send notification. */
    private void completeAndNotify(JobFlowDag flow) {
        ExecutionStatus finalStatus = JobFlowDagHelper.getFinalStatus(flow);
        jobFlowRun.setStatus(finalStatus);
        JobFlowRun newJobFlowRun = new JobFlowRun();
        newJobFlowRun.setId(jobFlowRun.getId());
        newJobFlowRun.setEndTime(LocalDateTime.now());
        newJobFlowRun.setStatus(finalStatus);
        jobFlowRunService.updateById(newJobFlowRun);

        // send notification.
        alertSendingService.sendAlerts(jobFlowRun);
    }

    // ! synchronized won't unmount the virtual thread, and thus block both its carrier and the underlying OS thread.
    // ! This doesn't make an application incorrect, but it might hinder its scalability.
    private synchronized void execVertex(JobVertex jobVertex, JobFlowDag flow) {
        if (!isRunning || AppRunner.isStopped()) {
            return;
        }

        if (runningJobs.containsKey(jobVertex.getId())) {
            log.warn("JobVertex: {} already executed", jobVertex.getId());
            return;
        }

        Supplier<JobResponse> runnable =
                new SemaphoreSupplier(semaphore, new JobExecuteThread(jobFlowRun.getId(), jobVertex, workerConfig));
        CompletableFuture<Void> jobVertexFuture = CompletableFuture.supplyAsync(runnable, jobExecService)
                .thenAccept(response -> handleResponse(response, jobVertex, flow));
        runningJobs.put(jobVertex.getId(), jobVertexFuture);
    }

    private void handleResponse(JobResponse jobResponse, JobVertex jobVertex, JobFlowDag flow) {
        jobVertex.setJobRunId(jobResponse.getJobRunId());
        jobVertex.setJobRunStatus(jobResponse.getStatus());

        ExecutionStatus finalStatus = jobResponse.getStatus();
        if (ExecutionStatus.isStopFlowState(finalStatus)) {
            killFlow();
            return;
        }

        for (JobVertex nextVertex : flow.getNextVertices(jobVertex)) {
            if (flow.isPreconditionSatisfied(nextVertex)) {
                execVertex(nextVertex, flow);
            }
        }
    }

    private void killFlow() {
        isRunning = false;
        // TODO：Better to cancel the running jobs?
        // Seems different scenarios have different results.
        runningJobs.values().forEach(future -> future.complete(null));
    }
}
