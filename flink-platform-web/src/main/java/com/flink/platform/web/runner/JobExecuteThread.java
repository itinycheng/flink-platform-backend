package com.flink.platform.web.runner;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobStatus;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.Worker;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.grpc.JobStatusReply;
import com.flink.platform.grpc.JobStatusRequest;
import com.flink.platform.grpc.ProcessJobReply;
import com.flink.platform.grpc.ProcessJobRequest;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.config.AppRunner;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.grpc.JobProcessGrpcClient;
import com.flink.platform.web.monitor.StatusInfo;
import com.flink.platform.web.service.WorkerApplyService;
import com.flink.platform.web.util.ThreadUtil;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Callable;

import static com.flink.platform.common.enums.ExecutionMode.STREAMING;
import static com.flink.platform.common.enums.ExecutionStatus.ERROR;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.NOT_EXIST;
import static com.flink.platform.common.enums.ExecutionStatus.RUNNING;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static com.flink.platform.grpc.JobGrpcServiceGrpc.JobGrpcServiceBlockingStub;

/** Execute job in a separate thread. */
@Slf4j
public class JobExecuteThread implements Callable<JobResponse> {

    private static final int MIN_SLEEP_TIME_MILLIS = 2000;

    private static final int MAX_SLEEP_TIME_MILLIS = 60_000;

    private final Long flowRunId;

    private final JobVertex jobVertex;

    private final int errorRetries;

    private final int streamingJobToSuccessMills;

    private final JobInfoService jobInfoService;

    private final JobRunInfoService jobRunInfoService;

    private final WorkerApplyService workerApplyService;

    private final JobProcessGrpcClient jobProcessGrpcClient;

    public JobExecuteThread(Long flowRunId, JobVertex jobVertex, WorkerConfig workerConfig) {
        this.flowRunId = flowRunId;
        this.jobVertex = jobVertex;
        this.errorRetries = workerConfig.getErrorRetries();
        this.streamingJobToSuccessMills = workerConfig.getStreamingJobToSuccessMills();
        this.jobInfoService = SpringContext.getBean(JobInfoService.class);
        this.jobRunInfoService = SpringContext.getBean(JobRunInfoService.class);
        this.workerApplyService = SpringContext.getBean(WorkerApplyService.class);
        this.jobProcessGrpcClient = SpringContext.getBean(JobProcessGrpcClient.class);
    }

    @Override
    public JobResponse call() {
        Long jobId = jobVertex.getJobId();
        Long jobRunId = jobVertex.getJobRunId();
        JobRunInfo jobRunInfo = null;

        try {
            // terminated job don't need to be processed
            ExecutionStatus jobRunStatus = jobVertex.getJobRunStatus();
            if (jobRunStatus != null && jobRunStatus.isTerminalState()) {
                return new JobResponse(jobId, jobRunId, jobRunStatus);
            }

            // Step 1: get job info
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
                jobRunInfo = processRemoteJob(stub, jobId);
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
            updateJobRunInfo(jobRunInfo, new StatusInfo(ERROR, null, System.currentTimeMillis()));
            return new JobResponse(jobId, jobRunId, ERROR);
        }
    }

    /** Send request to process remote job. */
    private JobRunInfo processRemoteJob(JobGrpcServiceBlockingStub stub, long jobId) {
        int retryTimes = 0;
        while (retryTimes++ <= errorRetries) {
            try {
                ProcessJobRequest.Builder request = ProcessJobRequest.newBuilder().setJobId(jobId);
                if (flowRunId != null) {
                    request.setFlowRunId(flowRunId);
                }
                ProcessJobReply reply = stub.processJob(request.build());
                return jobRunInfoService.getById(reply.getJobRunId());
            } catch (Exception e) {
                log.error("Process job: {} failed.", jobId, e);
                sleep(retryTimes);
            }
        }

        throw new IllegalStateException("Times to submit job exceeded limit: " + errorRetries);
    }

    public StatusInfo updateAndWaitForComplete(
            JobGrpcServiceBlockingStub stub, JobRunInfo jobRunInfo) {
        int retryTimes = 0;
        int errorTimes = 0;

        while (AppRunner.isRunning()) {
            try {
                JobStatusRequest request =
                        JobStatusRequest.newBuilder()
                                .setJobRunId(jobRunInfo.getId())
                                .setBackInfo(jobRunInfo.getBackInfo())
                                .setDeployMode(jobRunInfo.getDeployMode().name())
                                .build();
                JobStatusReply jobStatusReply = stub.getJobStatus(request);
                StatusInfo statusInfo = StatusInfo.fromReplay(jobStatusReply);
                if (jobRunInfo.getExecMode() == STREAMING) {
                    if (jobRunInfo.getCreateTime() == null) {
                        jobRunInfo.setCreateTime(LocalDateTime.now());
                    }
                    statusInfo = correctStreamJobStatus(statusInfo, jobRunInfo.getCreateTime());
                }

                if (statusInfo != null) {
                    log.info(
                            "Job runId: {}, name: {} Status: {}",
                            jobRunInfo.getJobId(),
                            jobRunInfo.getName(),
                            statusInfo.getStatus());
                    updateJobRunInfo(jobRunInfo, statusInfo);
                    if (statusInfo.getStatus().isTerminalState()) {
                        return statusInfo;
                    }
                }

            } catch (Exception e) {
                log.error("Fetch job status failed", e);
                if (errorTimes++ > errorRetries) {
                    long currentTimeMillis = System.currentTimeMillis();
                    return new StatusInfo(ERROR, currentTimeMillis, currentTimeMillis);
                }
            }

            sleep(++retryTimes);
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

    private void updateJobRunInfo(JobRunInfo jobRunInfo, StatusInfo statusInfo) {
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
            jobRunInfoService.updateById(newJobRun);
            jobRunInfo.setStatus(statusInfo.getStatus());
        } catch (Exception e) {
            log.error("Update job run status failed", e);
        }
    }

    private void sleep(int retryTimes) {
        int mills = Math.min(retryTimes * MIN_SLEEP_TIME_MILLIS, MAX_SLEEP_TIME_MILLIS);
        ThreadUtil.sleep(mills);
    }
}
