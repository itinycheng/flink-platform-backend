package com.flink.platform.web.runner;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.dao.entity.JobFlowDag;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.util.JobFlowDagHelper;
import com.flink.platform.web.util.ThreadUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import static com.flink.platform.common.enums.ExecutionStatus.ERROR;
import static com.flink.platform.common.enums.ExecutionStatus.NOT_EXIST;

/** Process flow in a separate thread. */
@Slf4j
public class FlowExecuteThread implements Runnable {

    private final JobFlowRun jobFlowRun;

    private final WorkerConfig workerConfig;

    private final ExecutorService jobExecService;

    private final List<CompletableFuture<Void>> runningJobs =
            Collections.synchronizedList(new ArrayList<>());

    private final JobFlowRunService jobFlowRunService =
            SpringContext.getBean(JobFlowRunService.class);

    private volatile boolean isRunning = true;

    public FlowExecuteThread(JobFlowRun jobFlowRun, WorkerConfig workerConfig) {
        this.jobFlowRun = jobFlowRun;
        this.workerConfig = workerConfig;
        this.jobExecService =
                ThreadUtil.newFixedThreadExecutor(
                        String.format("FlowExecThread-runId_%d", jobFlowRun.getId()),
                        workerConfig.getPerFlowExecThreads());
    }

    @Override
    public void run() {
        JobFlowDag flow = jobFlowRun.getFlow();
        flow.getBeginVertices().forEach(jobVertex -> execVertex(jobVertex, flow));

        // Wait for all jobs complete.
        CompletableFuture.allOf(runningJobs.toArray(new CompletableFuture[0]))
                .thenAccept(unused -> updateJobFlow(flow));
    }

    // Update status of jobFlow.
    private void updateJobFlow(JobFlowDag flow) {
        ExecutionStatus finalStatus = JobFlowDagHelper.getDagState(flow);
        if (finalStatus != null && finalStatus.isTerminalState()) {
            JobFlowRun newJobFlowRun = new JobFlowRun();
            newJobFlowRun.setId(jobFlowRun.getId());
            newJobFlowRun.setStatus(finalStatus);
            jobFlowRunService.updateById(newJobFlowRun);
        }
    }

    private void execVertex(JobVertex jobVertex, JobFlowDag flow) {
        Supplier<JobResponse> runnable =
                () -> new JobExecuteThread(jobFlowRun.getId(), jobVertex, workerConfig).call();
        CompletableFuture<Void> jobVertexFuture =
                CompletableFuture.supplyAsync(runnable, jobExecService)
                        .thenAccept(response -> handleResponse(response, jobVertex, flow));
        runningJobs.add(jobVertexFuture);
    }

    private void handleResponse(JobResponse jobResponse, JobVertex jobVertex, JobFlowDag flow) {
        if (!isRunning) {
            return;
        }

        jobVertex.setJobRunId(jobResponse.getJobRunId());
        jobVertex.setJobRunStatus(jobResponse.getStatus());

        ExecutionStatus finalStatus = jobResponse.getStatus();
        if (finalStatus == ERROR || finalStatus == NOT_EXIST) {
            killFlow();
            return;
        }

        for (JobVertex nextVertex : flow.getNextVertices(jobVertex)) {
            if (JobFlowDagHelper.isPreconditionSatisfied(nextVertex, flow)) {
                execVertex(nextVertex, flow);
            }
        }
    }

    /** TODO: interrupt thread is a better choose, but not support by CompleteFuture. */
    private void killFlow() {
        isRunning = false;
        runningJobs.forEach(future -> future.complete(null));
    }
}
