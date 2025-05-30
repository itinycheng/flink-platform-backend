package com.flink.platform.web.runner;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobStatus;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.common.util.ExceptionUtil;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.result.JobCallback;
import com.flink.platform.dao.entity.task.BaseJob;
import com.flink.platform.dao.entity.task.FlinkJob;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.grpc.JobStatusReply;
import com.flink.platform.grpc.JobStatusRequest;
import com.flink.platform.grpc.ProcessJobReply;
import com.flink.platform.grpc.ProcessJobRequest;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.config.AppRunner;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.grpc.JobGrpcClient;
import com.flink.platform.web.monitor.StatusInfo;
import com.flink.platform.web.service.JobRunExtraService;
import com.flink.platform.web.util.ThreadUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Supplier;

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
import static com.flink.platform.web.util.ThreadUtil.MIN_SLEEP_TIME_MILLIS;
import static java.util.Objects.nonNull;

/** Execute job in a separate thread. */
@Slf4j
public class JobExecuteThread implements Supplier<JobResponse> {

    private final Long flowRunId;

    private final Long jobId;

    private final int errorRetries;

    private final int streamingJobToSuccessMills;

    private final JobInfoService jobInfoService;

    private final JobRunInfoService jobRunInfoService;

    private final JobRunExtraService jobRunExtraService;

    private final JobFlowRunService jobFlowRunService;

    private final JobGrpcClient jobGrpcClient;

    private Long jobRunId;

    private ExecutionStatus jobRunStatus;

    public JobExecuteThread(Long flowRunId, JobVertex jobVertex, WorkerConfig workerConfig) {
        this.flowRunId = flowRunId;
        this.jobId = jobVertex.getJobId();
        this.errorRetries = workerConfig.getErrorRetries();
        this.streamingJobToSuccessMills = workerConfig.getStreamingJobToSuccessMills();
        this.jobInfoService = SpringContext.getBean(JobInfoService.class);
        this.jobRunInfoService = SpringContext.getBean(JobRunInfoService.class);
        this.jobRunExtraService = SpringContext.getBean(JobRunExtraService.class);
        this.jobFlowRunService = SpringContext.getBean(JobFlowRunService.class);
        this.jobGrpcClient = SpringContext.getBean(JobGrpcClient.class);
    }

    @SuppressWarnings("t")
    @Override
    public JobResponse get() {
        int retryTimes;
        Duration retryInterval;
        int retryAttempt;
        try {
            // 1. get retry info.
            BaseJob baseJob = getRetryConfig();
            retryTimes = baseJob.getRetryTimes();
            retryInterval = baseJob.parseRetryInterval();

            // 2. load execution count and the last jobRun status if it exists.
            Pair<Integer, JobRunInfo> pair = getCountAndLastJobRun();
            retryAttempt = pair.getLeft() - 1;
            JobRunInfo jobRun = pair.getRight();
            if (jobRun != null) {
                jobRunId = jobRun.getId();
                jobRunStatus = jobRun.getStatus();
            }
        } catch (Exception e) {
            log.error("error", e);
            return new JobResponse(jobId, jobRunId, ERROR);
        }

        // 3. execute job if possible.
        while (AppRunner.isRunning()) {
            if (SUCCESS.equals(jobRunStatus)) {
                return new JobResponse(jobId, jobRunId, SUCCESS);
            }

            if (noRunningJobs() && ++retryAttempt > retryTimes) {
                return new JobResponse(jobId, jobRunId, jobRunStatus);
            }

            if (isFlowRunStopped()) {
                return new JobResponse(jobId, jobRunId, KILLED);
            }

            callOnce();
            if (SUCCESS.equals(jobRunStatus)) {
                return new JobResponse(jobId, jobRunId, SUCCESS);
            }

            // sleep and retry if exception found or status isn't success.
            log.warn("Execute jobRun: {} and wait for complete failed, retry attempt: {}.", jobRunId, retryAttempt);
            if (retryAttempt < retryTimes) {
                sleepRetry(retryInterval);
            }
        }

        ExecutionStatus finalStatus = null;
        if (retryAttempt > retryTimes || SUCCESS.equals(jobRunStatus)) {
            finalStatus = jobRunStatus;
        }
        return new JobResponse(jobId, jobRunId, finalStatus);
    }

    public void callOnce() {
        JobRunInfo jobRun = null;
        try {
            // Get job info, return if not found.
            JobInfo jobInfo = jobInfoService.getOne(new QueryWrapper<JobInfo>()
                    .lambda()
                    .eq(JobInfo::getId, jobId)
                    .eq(JobInfo::getStatus, JobStatus.ONLINE));
            if (jobInfo == null) {
                log.warn("The job:{} is no longer exists or not in ready/scheduled status.", jobId);
                jobRunStatus = NOT_EXIST;
                return;
            }

            // Get or create new jobRun.
            if (jobRunId == null || jobRunStatus.isTerminalState()) {
                jobRun = getOrCreateJobRun(jobInfo);
            } else {
                jobRun = jobRunInfoService.getById(jobRunId);
                log.info("Job:{} already submitted, runId = {}, status: {}.", jobId, jobRunId, jobRun.getStatus());
            }

            // Update jobRunId and jobRunStatus in memory.
            jobRunId = jobRun.getId();
            jobRunStatus = jobRun.getStatus();

            // Get a grpc client.
            JobGrpcServiceBlockingStub stub = jobGrpcClient.grpcClient(jobRun.getHost());

            // Process job.
            if (CREATED.equals(jobRunStatus)) {
                jobRun = processRemoteJob(stub, jobRunId);
                jobRunStatus = jobRun.getStatus();
            }

            // Wait for job to complete and get status
            if (jobRunStatus == null || !jobRunStatus.isTerminalState()) {
                StatusInfo statusInfo = updateAndWaitForComplete(stub, jobRun);
                if (statusInfo != null) {
                    jobRunStatus = statusInfo.getStatus();
                }
            }
        } catch (Exception e) {
            jobRunStatus = ERROR;
            updateJobRunIfNeeded(jobRun, new StatusInfo(ERROR, null, System.currentTimeMillis()), e);
            log.error("Exception found when executing jobRun: {} and wait for complete", jobRunId, e);
        }
    }

    private BaseJob getRetryConfig() {
        int retry = 0;
        while (AppRunner.isRunning() && retry++ < 3) {
            try {
                JobInfo jobInfo = jobInfoService.getById(jobId);
                return jobInfo.getConfig();
            } catch (Throwable t) {
                log.error("Get base job info failed", t);
                ThreadUtil.sleep(1000);
            }
        }

        throw new RuntimeException("Get job info failed");
    }

    private Pair<Integer, JobRunInfo> getCountAndLastJobRun() {
        int retry = 0;
        while (AppRunner.isRunning() && retry++ < 3) {
            try {
                List<JobRunInfo> jobRuns = jobRunInfoService.list(new QueryWrapper<JobRunInfo>()
                        .lambda()
                        .select(JobRunInfo::getId, JobRunInfo::getStatus)
                        .eq(JobRunInfo::getJobId, jobId)
                        .eq(JobRunInfo::getFlowRunId, flowRunId)
                        .orderByDesc(JobRunInfo::getId));
                if (CollectionUtils.isNotEmpty(jobRuns)) {
                    return Pair.of(jobRuns.size(), jobRuns.getFirst());
                } else {
                    return Pair.of(0, null);
                }
            } catch (Throwable t) {
                log.error("Get base job info failed", t);
                ThreadUtil.sleep(1000);
            }
        }

        throw new RuntimeException("Get execution times and last jobRun failed");
    }

    // --------------------------------------------------------------------------------------------
    // The following methods should be called in while/retry loop.
    // --------------------------------------------------------------------------------------------

    public void sleepRetry(Duration interval) {
        if (interval == null || !interval.isPositive()) {
            ThreadUtil.sleep(MIN_SLEEP_TIME_MILLIS);
            return;
        }

        var remaining = interval.toMillis();
        while (AppRunner.isRunning() && remaining > 0) {
            if (isFlowRunStopped()) {
                return;
            }

            var tmp = remaining;
            remaining = remaining - MIN_SLEEP_TIME_MILLIS;
            ThreadUtil.sleep(remaining > 0 ? MIN_SLEEP_TIME_MILLIS : tmp);
        }
    }

    private JobRunInfo getOrCreateJobRun(JobInfo jobInfo) {
        JobRunInfo jobRun = jobRunInfoService.getOne(new QueryWrapper<JobRunInfo>()
                .lambda()
                .eq(JobRunInfo::getJobId, jobInfo.getId())
                .eq(nonNull(flowRunId), JobRunInfo::getFlowRunId, flowRunId)
                .eq(JobRunInfo::getStatus, CREATED)
                .last("LIMIT 1"));
        if (jobRun != null) {
            return jobRun;
        }

        Long jobRunId = jobRunExtraService.createJobRun(jobInfo, flowRunId);
        return jobRunInfoService.getById(jobRunId);
    }

    /** process job. */
    private JobRunInfo processRemoteJob(JobGrpcServiceBlockingStub stub, long jobRunId) {
        ProcessJobRequest.Builder request = ProcessJobRequest.newBuilder().setJobRunId(jobRunId);
        ProcessJobReply reply = stub.processJob(request.build());
        return jobRunInfoService.getById(reply.getJobRunId());
    }

    public StatusInfo updateAndWaitForComplete(JobGrpcServiceBlockingStub stub, JobRunInfo jobRun) {
        int retryTimes = 0;
        while (AppRunner.isRunning()) {
            try {
                // Get and correct job status.
                JobStatusRequest request = buildJobStatusRequest(jobRun);
                JobStatusReply jobStatusReply = stub.getJobStatus(request);
                StatusInfo statusInfo = StatusInfo.fromReplay(jobStatusReply);
                if (STREAMING.equals(jobRun.getExecMode())) {
                    // Interim solution: finite data streams can also use streaming mode.
                    FlinkJob flinkJob = jobRun.getConfig().unwrap(FlinkJob.class);
                    if (flinkJob != null && !flinkJob.isWaitForTermination()) {
                        statusInfo = correctStreamJobStatus(statusInfo, jobRun.getCreateTime());
                    }
                }

                // update status.
                if (statusInfo != null) {
                    log.info(
                            "Job runId: {}, name: {} Status: {}",
                            jobRun.getId(),
                            jobRun.getName(),
                            statusInfo.getStatus());
                    updateJobRunIfNeeded(jobRun, statusInfo, null);
                    if (statusInfo.getStatus().isTerminalState()) {
                        return statusInfo;
                    }
                }
            } catch (Exception e) {
                log.error("Fetch job status failed", e);
            }

            ThreadUtil.sleepRetry(++retryTimes);
        }

        return null;
    }

    private JobStatusRequest buildJobStatusRequest(JobRunInfo jobRun) {
        JobStatusRequest.Builder builder = JobStatusRequest.newBuilder()
                .setJobRunId(jobRun.getId())
                .setDeployMode(jobRun.getDeployMode().name())
                .setRetries(errorRetries);

        JobCallback callback = jobRun.getBackInfo();
        if (callback != null) {
            callback = callback.cloneWithoutMsg();
            builder.setBackInfo(JsonUtil.toJsonString(callback));
        }

        return builder.build();
    }

    private StatusInfo correctStreamJobStatus(StatusInfo statusInfo, LocalDateTime startTime) {
        if (statusInfo == null || statusInfo.getStatus().isTerminalState()) {
            return statusInfo;
        }

        if (LocalDateTime.now().isAfter(startTime.plus(streamingJobToSuccessMills, ChronoUnit.MILLIS))) {
            ExecutionStatus finalStatus = statusInfo.getStatus() == RUNNING ? SUCCESS : FAILURE;
            return new StatusInfo(finalStatus, statusInfo.getStartTime(), statusInfo.getEndTime());
        }

        return statusInfo;
    }

    private void updateJobRunIfNeeded(JobRunInfo jobRunInfo, StatusInfo statusInfo, Exception exception) {
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
                newJobRun.setEndTime(endTime);
            }
            if (exception != null) {
                String exceptionMsg = ExceptionUtil.stackTrace(exception);
                newJobRun.setBackInfo(new JobCallback(exceptionMsg, null));
            }

            jobRunInfoService.updateById(newJobRun);
            jobRunInfo.setStatus(statusInfo.getStatus());
        } catch (Exception e) {
            log.error("Update job run status failed", e);
        }
    }

    private boolean isFlowRunStopped() {
        try {
            JobFlowRun jobFlowRun = jobFlowRunService.getOne(new QueryWrapper<JobFlowRun>()
                    .lambda()
                    .select(JobFlowRun::getStatus)
                    .eq(JobFlowRun::getId, flowRunId));
            ExecutionStatus flowStatus = jobFlowRun.getStatus();
            return KILLABLE.equals(flowStatus) || flowStatus.isTerminalState();
        } catch (Exception exception) {
            log.error("Get flow run: {} status failed", flowRunId, exception);
            return false;
        }
    }

    private boolean noRunningJobs() {
        return jobRunStatus == null || jobRunStatus.isTerminalState();
    }
}
