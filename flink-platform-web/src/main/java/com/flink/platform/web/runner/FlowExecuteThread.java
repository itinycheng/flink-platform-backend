package com.flink.platform.web.runner;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.dao.entity.JobFlowDag;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.service.AlertSendingService;
import com.flink.platform.web.util.JobFlowDagHelper;
import com.flink.platform.web.util.ThreadUtil;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import static com.flink.platform.common.enums.ExecutionStatus.RUNNING;

/** Process flow in a separate thread. */
@Slf4j
public class FlowExecuteThread implements Runnable {

    private final JobFlowRun jobFlowRun;

    private final WorkerConfig workerConfig;

    private final ExecutorService jobExecService;

    private final Map<Long, CompletableFuture<Void>> runningJobs = new ConcurrentHashMap<>();

    private final JobFlowRunService jobFlowRunService =
            SpringContext.getBean(JobFlowRunService.class);

    private final AlertSendingService alertSendingService =
            SpringContext.getBean(AlertSendingService.class);

    private volatile boolean isRunning = true;

    public FlowExecuteThread(JobFlowRun jobFlowRun, WorkerConfig workerConfig) {
        this.jobFlowRun = jobFlowRun;
        this.workerConfig = workerConfig;
        this.jobExecService =
                ThreadUtil.newFixedThreadExecutor(
                        String.format("FlowExecThread-flowRunId_%d", jobFlowRun.getId()),
                        workerConfig.getPerFlowExecThreads());
    }

    @Override
    public void run() {
        // Update status of jobFlowRun.
        JobFlowRun newJobFlowRun = new JobFlowRun();
        newJobFlowRun.setId(jobFlowRun.getId());
        newJobFlowRun.setStatus(RUNNING);
        jobFlowRunService.updateById(newJobFlowRun);

        // Process job flow.
        JobFlowDag flow = jobFlowRun.getFlow();
        flow.getBeginVertices().forEach(jobVertex -> execVertex(jobVertex, flow));

        // Wait until all vertices are executed.
        while (isRunning && JobFlowDagHelper.hasUnExecutedVertices(flow)) {
            ThreadUtil.sleep(5000);
        }

        // Wait for all jobs complete.
        CompletableFuture.allOf(runningJobs.values().toArray(new CompletableFuture[0]))
                .thenAccept(unused -> completeAndNotify(flow))
                .thenAccept(unused -> jobExecService.shutdownNow());
    }

    /** Update status of jobFlow and send notification. */
    private void completeAndNotify(JobFlowDag flow) {
        ExecutionStatus finalStatus = JobFlowDagHelper.getDagState(flow);
        if (finalStatus.isTerminalState()) {
            jobFlowRun.setStatus(finalStatus);
            JobFlowRun newJobFlowRun = new JobFlowRun();
            newJobFlowRun.setId(jobFlowRun.getId());
            newJobFlowRun.setEndTime(LocalDateTime.now());
            newJobFlowRun.setStatus(finalStatus);
            jobFlowRunService.updateById(newJobFlowRun);

            // send notification.
            alertSendingService.sendAlerts(jobFlowRun);
        }
    }

    private synchronized void execVertex(JobVertex jobVertex, JobFlowDag flow) {
        if (runningJobs.containsKey(jobVertex.getId())) {
            log.warn("JobVertex: {} already executed", jobVertex.getId());
            return;
        }

        Supplier<JobResponse> runnable =
                () -> new JobExecuteThread(jobFlowRun.getId(), jobVertex, workerConfig).call();
        CompletableFuture<Void> jobVertexFuture =
                CompletableFuture.supplyAsync(runnable, jobExecService)
                        .thenAccept(response -> handleResponse(response, jobVertex, flow));
        runningJobs.put(jobVertex.getId(), jobVertexFuture);
    }

    private void handleResponse(JobResponse jobResponse, JobVertex jobVertex, JobFlowDag flow) {
        if (!isRunning) {
            return;
        }

        ExecutionStatus finalStatus = jobResponse.getStatus();
        if (ExecutionStatus.isStopFlowState(finalStatus)) {
            killFlow();
            return;
        }

        jobVertex.setJobRunId(jobResponse.getJobRunId());
        jobVertex.setJobRunStatus(jobResponse.getStatus());

        for (JobVertex nextVertex : flow.getNextVertices(jobVertex)) {
            if (JobFlowDagHelper.isPreconditionSatisfied(nextVertex, flow)) {
                execVertex(nextVertex, flow);
            }
        }
    }

    private void killFlow() {
        isRunning = false;
        // TODO：kill unfinished jobs.
        runningJobs.values().forEach(future -> future.complete(null));
    }
}
