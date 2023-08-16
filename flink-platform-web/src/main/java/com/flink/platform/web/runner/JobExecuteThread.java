package com.flink.platform.web.runner;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobStatus;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.Worker;
import com.flink.platform.dao.entity.task.FlinkJob;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.grpc.JobStatusReply;
import com.flink.platform.grpc.JobStatusRequest;
import com.flink.platform.grpc.ProcessJobReply;
import com.flink.platform.grpc.ProcessJobRequest;
import com.flink.platform.web.command.JobCallback;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.config.AppRunner;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.grpc.JobProcessGrpcClient;
import com.flink.platform.web.monitor.StatusInfo;
import com.flink.platform.web.service.WorkerApplyService;
import com.flink.platform.web.util.ThreadUtil;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Callable;

import static com.flink.platform.common.enums.ExecutionMode.STREAMING;
import static com.flink.platform.common.enums.ExecutionStatus.CREATED;
import static com.flink.platform.common.enums.ExecutionStatus.ERROR;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.KILLABLE;
import static com.flink.platform.common.enums.ExecutionStatus.KILLED;
import static com.flink.platform.common.enums.ExecutionStatus.NOT_EXIST;
import static com.flink.platform.common.enums.ExecutionStatus.RUNNING;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static com.flink.platform.grpc.JobGrpcServiceGrpc.JobGrpcServiceBlockingStub;
import static java.util.Objects.nonNull;

/** Execute job in a separate thread. */
@Slf4j
public class JobExecuteThread implements Callable<JobResponse> {

    private final Long flowRunId;

    private final JobVertex jobVertex;

    private final int errorRetries;

    private final int streamingJobToSuccessMills;

    private final JobInfoService jobInfoService;

    private final JobRunInfoService jobRunInfoService;

    private final JobFlowRunService jobFlowRunService;

    private final WorkerApplyService workerApplyService;

    private final JobProcessGrpcClient jobProcessGrpcClient;

    public JobExecuteThread(Long flowRunId, JobVertex jobVertex, WorkerConfig workerConfig) {
        this.flowRunId = flowRunId;
        this.jobVertex = jobVertex;
        this.errorRetries = workerConfig.getErrorRetries();
        this.streamingJobToSuccessMills = workerConfig.getStreamingJobToSuccessMills();
        this.jobInfoService = SpringContext.getBean(JobInfoService.class);
        this.jobRunInfoService = SpringContext.getBean(JobRunInfoService.class);
        this.jobFlowRunService = SpringContext.getBean(JobFlowRunService.class);
        this.workerApplyService = SpringContext.getBean(WorkerApplyService.class);
        this.jobProcessGrpcClient = SpringContext.getBean(JobProcessGrpcClient.class);
    }

    @Nonnull
    @Override
    public JobResponse call() {
        Long jobId = jobVertex.getJobId();
        Long jobRunId = jobVertex.getJobRunId();
        JobRunInfo jobRunInfo = null;

        try {
            // Terminated job don't need to be processed again.
            ExecutionStatus jobRunStatus = jobVertex.getJobRunStatus();
            if (jobRunStatus != null && jobRunStatus.isTerminalState()) {
                return new JobResponse(jobId, jobRunId, jobRunStatus);
            }

            // Check workflow status and return if KILLABLE.
            if (flowRunId != null) {
                JobFlowRun jobFlowRun =
                        jobFlowRunService.getOne(
                                new QueryWrapper<JobFlowRun>()
                                        .lambda()
                                        .select(JobFlowRun::getStatus)
                                        .eq(JobFlowRun::getId, flowRunId));
                ExecutionStatus status = jobFlowRun.getStatus();
                if (KILLABLE.equals(status) || status.isTerminalState()) {
                    return new JobResponse(jobId, jobRunId, KILLED);
                }
            }

            // Step 1: get job info and return if null.
            JobInfo jobInfo =
                    jobInfoService.getOne(
                            new QueryWrapper<JobInfo>()
                                    .lambda()
                                    .eq(JobInfo::getId, jobId)
                                    .eq(JobInfo::getStatus, JobStatus.ONLINE));
            if (jobInfo == null) {
                log.warn("The job:{} is no longer exists or not in ready/scheduled status.", jobId);
                return new JobResponse(jobId, jobRunId, NOT_EXIST);
            }

            // Step 2: random a grpc client.
            Worker worker = workerApplyService.randomWorker(jobInfo.getRouteUrl());
            JobGrpcServiceBlockingStub stub = jobProcessGrpcClient.grpcClient(worker);

            // Step 3: process job and get jobRun.
            if (jobRunId != null) {
                jobRunInfo = jobRunInfoService.getById(jobRunId);
                log.info("Job:{} already submitted, runId = {}.", jobId, jobRunId);
            } else {
                // Create a job run record if needed.
                jobRunInfo =
                        jobRunInfoService.getOne(
                                new QueryWrapper<JobRunInfo>()
                                        .lambda()
                                        .eq(JobRunInfo::getJobId, jobId)
                                        .eq(nonNull(flowRunId), JobRunInfo::getFlowRunId, flowRunId)
                                        .eq(JobRunInfo::getStatus, CREATED)
                                        .last("LIMIT 1"));
                if (jobRunInfo == null) {
                    String workerIp = worker != null ? worker.getIp() : Constant.HOST_IP;
                    jobRunInfo = jobRunInfoService.createFrom(jobInfo, flowRunId, workerIp);
                    jobRunInfoService.save(jobRunInfo);
                }

                // Process job run.
                jobRunInfo = processRemoteJob(stub, jobRunInfo.getId());
            }

            if (jobRunInfo == null) {
                log.warn("The jobRun:{} is no longer exists.", jobRunId);
                return new JobResponse(jobId, jobRunId, NOT_EXIST);
            }

            // Step 4: Update jobRunId in Memory.
            jobRunId = jobRunInfo.getId();

            // Step 5: Wait for job complete and get final status.
            ExecutionStatus status = jobRunInfo.getStatus();
            if (status == null || !status.isTerminalState()) {
                StatusInfo statusInfo = updateAndWaitForComplete(stub, jobRunInfo);
                if (statusInfo != null) {
                    status = statusInfo.getStatus();
                }
            }

            return new JobResponse(jobId, jobRunId, status);
        } catch (Exception e) {
            log.error("Submit job and wait for complete failed.", e);
            updateJobRunIfNeeded(
                    jobRunInfo, new StatusInfo(ERROR, null, System.currentTimeMillis()), e);
            return new JobResponse(jobId, jobRunId, ERROR);
        }
    }

    /**
     * Send request to process remote job. <br>
     * TODO: Failed retries should be narrow down to job script submission.
     */
    private JobRunInfo processRemoteJob(JobGrpcServiceBlockingStub stub, long jobRunId) {
        ProcessJobRequest.Builder request =
                ProcessJobRequest.newBuilder().setJobRunId(jobRunId).setRetries(errorRetries);
        ProcessJobReply reply = stub.processJob(request.build());
        return jobRunInfoService.getById(reply.getJobRunId());
    }

    public StatusInfo updateAndWaitForComplete(
            JobGrpcServiceBlockingStub stub, JobRunInfo jobRunInfo) {
        int retryCount = 0;
        while (AppRunner.isRunning()) {
            try {
                JobStatusRequest request =
                        JobStatusRequest.newBuilder()
                                .setJobRunId(jobRunInfo.getId())
                                .setBackInfo(jobRunInfo.getBackInfo())
                                .setDeployMode(jobRunInfo.getDeployMode().name())
                                .setRetries(errorRetries)
                                .build();
                JobStatusReply jobStatusReply = stub.getJobStatus(request);
                StatusInfo statusInfo = StatusInfo.fromReplay(jobStatusReply);
                if (jobRunInfo.getExecMode() == STREAMING) {
                    if (jobRunInfo.getCreateTime() == null) {
                        jobRunInfo.setCreateTime(LocalDateTime.now());
                    }

                    // Interim solution: finite data streams can also use streaming mode.
                    FlinkJob flinkJob = jobRunInfo.getConfig().unwrap(FlinkJob.class);
                    if (flinkJob != null && !flinkJob.isWaitForTermination()) {
                        statusInfo = correctStreamJobStatus(statusInfo, jobRunInfo.getCreateTime());
                    }
                }

                if (statusInfo != null) {
                    log.info(
                            "Job runId: {}, name: {} Status: {}",
                            jobRunInfo.getId(),
                            jobRunInfo.getName(),
                            statusInfo.getStatus());
                    updateJobRunIfNeeded(jobRunInfo, statusInfo, null);
                    if (statusInfo.getStatus().isTerminalState()) {
                        return statusInfo;
                    }
                }

            } catch (Exception e) {
                log.error("Fetch job status failed", e);
            }

            ThreadUtil.sleepRetry(++retryCount);
        }

        return null;
    }

    private StatusInfo correctStreamJobStatus(StatusInfo statusInfo, LocalDateTime startTime) {
        if (statusInfo == null || statusInfo.getStatus().isTerminalState()) {
            return statusInfo;
        }

        if (LocalDateTime.now()
                .isAfter(startTime.plus(streamingJobToSuccessMills, ChronoUnit.MILLIS))) {
            ExecutionStatus finalStatus = statusInfo.getStatus() == RUNNING ? SUCCESS : FAILURE;
            return new StatusInfo(finalStatus, statusInfo.getStartTime(), statusInfo.getEndTime());
        }

        return statusInfo;
    }

    private void updateJobRunIfNeeded(
            JobRunInfo jobRunInfo, StatusInfo statusInfo, Exception exception) {
        try {
            if (jobRunInfo == null || jobRunInfo.getId() == null) {
                return;
            }

            if (jobRunInfo.getStatus() == statusInfo.getStatus()) {
                return;
            }

            JobRunInfo newJobRun = new JobRunInfo();
            newJobRun.setId(jobRunInfo.getId());
            newJobRun.setStatus(statusInfo.getStatus());
            if (statusInfo.getStatus().isTerminalState()) {
                LocalDateTime endTime = statusInfo.toEndTime();
                if (endTime == null) {
                    endTime = LocalDateTime.now();
                }
                newJobRun.setStopTime(endTime);
            }
            if (exception != null) {
                newJobRun.setBackInfo(
                        JsonUtil.toJsonString(new JobCallback(exception.getMessage(), null)));
            }

            jobRunInfoService.updateById(newJobRun);
            jobRunInfo.setStatus(statusInfo.getStatus());
        } catch (Exception e) {
            log.error("Update job run status failed", e);
        }
    }
}
