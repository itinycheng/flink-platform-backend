package com.flink.platform.web.runner;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobStatus;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.common.util.ExceptionUtil;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.result.JobCallback;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.grpc.JobStatusRequest;
import com.flink.platform.grpc.ProcessJobRequest;
import com.flink.platform.web.common.MetadataFailure;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.grpc.JobGrpcClient;
import com.flink.platform.web.jobrun.JobRunStateService;
import com.flink.platform.web.lifecycle.AppRunner;
import com.flink.platform.web.monitor.StatusInfo;
import com.flink.platform.web.service.JobRunExtraService;
import com.flink.platform.web.service.JobRunExtraService.CreateResult;
import com.flink.platform.web.util.ThreadUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static com.flink.platform.common.constants.Constant.HOST_IP;
import static com.flink.platform.common.enums.ExecutionStatus.CREATED;
import static com.flink.platform.common.enums.ExecutionStatus.ERROR;
import static com.flink.platform.common.enums.ExecutionStatus.KILLED;
import static com.flink.platform.common.enums.ExecutionStatus.KILLING;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static com.flink.platform.grpc.JobGrpcServiceGrpc.JobGrpcServiceBlockingStub;
import static com.flink.platform.web.jobrun.JobRunTransition.Outcome.APPLIED;
import static com.flink.platform.web.util.ThreadUtil.FIVE_SECOND_MILLIS;
import static com.flink.platform.web.util.ThreadUtil.THREE_SECOND_MILLIS;
import static java.util.Objects.requireNonNull;

/** Execute job in a separate thread. */
@Slf4j
public class JobExecuteThread implements Supplier<JobResponse> {

    private final Long flowRunId;

    private final Long jobId;

    private final BooleanSupplier localRunAborted;

    private final JobInfoService jobInfoService;

    private final JobRunInfoService jobRunInfoService;

    private final JobRunStateService jobRunStateService;

    private final JobRunExtraService jobRunExtraService;

    private final JobFlowRunService flowRunService;

    private final JobGrpcClient jobGrpcClient;

    @Nullable
    private Long jobRunId;

    @Nullable
    private ExecutionStatus jobRunStatus;

    public JobExecuteThread(Long flowRunId, JobVertex jobVertex, BooleanSupplier localRunAborted) {
        this.flowRunId = flowRunId;
        this.jobId = jobVertex.getJobId();
        this.localRunAborted = localRunAborted;
        this.jobInfoService = SpringContext.getBean(JobInfoService.class);
        this.jobRunInfoService = SpringContext.getBean(JobRunInfoService.class);
        this.jobRunStateService = SpringContext.getBean(JobRunStateService.class);
        this.jobRunExtraService = SpringContext.getBean(JobRunExtraService.class);
        this.flowRunService = SpringContext.getBean(JobFlowRunService.class);
        this.jobGrpcClient = SpringContext.getBean(JobGrpcClient.class);
    }

    @Override
    public JobResponse get() {
        if (localRunAborted.getAsBoolean()) {
            return JobResponse.ABORTED;
        }

        try {
            return execute();
        } catch (Exception e) {
            if (MetadataFailure.isUnavailable(e)) {
                log.warn("Abort local job execution, jobId: {}, flowRunId: {}: metadata unavailable", jobId, flowRunId);
                return JobResponse.ABORTED;
            } else {
                log.error("Job execution failed, jobId: {}, flowRunId: {}", jobId, flowRunId, e);
                return response(ERROR);
            }
        }
    }

    private JobResponse execute() {
        var retryAndRun = getRetryAndLatestRun();
        jobRunId = retryAndRun.jobRunId();
        jobRunStatus = retryAndRun.jobRunStatus();
        if (SUCCESS.equals(jobRunStatus)) {
            return response(SUCCESS);
        }

        var retryTimes = retryAndRun.retryTimes();
        var retryInterval = retryAndRun.retryInterval();
        var retryAttempt = retryAndRun.retryAttempt();
        while (AppRunner.isRunning()) {
            var flowAction = checkFlowAction();
            if (flowAction != FlowAction.PROCEED) {
                return stopResponse(flowAction);
            }

            if (noRunInProgress() && ++retryAttempt > retryTimes) {
                return response(jobRunStatusOrError());
            }

            if (callOnce()) {
                return JobResponse.ABORTED;
            }

            if (SUCCESS.equals(jobRunStatus)) {
                return response(SUCCESS);
            }

            // sleep and retry if exception found or status isn't success.
            log.warn("Execute jobRun: {} and wait for complete failed, retry attempt: {}.", jobRunId, retryAttempt);
            if (retryAttempt < retryTimes) {
                sleepRetry(retryInterval);
            }
        }

        // The loop only exits when the app is stopping; leave the run for the next owner.
        return JobResponse.ABORTED;
    }

    private boolean callOnce() {
        JobRunInfo jobRun = null;
        try {
            // Get or create new jobRun.
            var jobInfo = jobInfoService.findJobByIdAndStatus(jobId, JobStatus.ONLINE);
            if (jobRunId == null || jobRunStatus == null || jobRunStatus.isTerminalState()) {
                jobRun = getOrCreateJobRun(requireNonNull(jobInfo));
            } else {
                jobRun = requireNonNull(jobRunInfoService.getById(jobRunId));
                log.info("Job:{} already submitted, runId = {}, status: {}.", jobId, jobRunId, jobRun.getStatus());
            }

            if (jobRun == null) {
                return true;
            }

            // Update jobRunId and jobRunStatus in memory.
            jobRunId = jobRun.getId();
            jobRunStatus = jobRun.getStatus();
            if (jobRunStatus.isTerminalState()) {
                return false;
            }

            if (localRunAborted.getAsBoolean()) {
                return true;
            }

            // Process job
            var stub = jobGrpcClient.grpcClient(jobRun.getHost());
            if (CREATED.equals(jobRunStatus)) {
                jobRun = processRemoteJob(stub, requireNonNull(jobRunId));
                jobRunStatus = jobRun.getStatus();
            }

            // Wait for job to complete and get status
            if (jobRunStatus == null || !jobRunStatus.isTerminalState()) {
                var statusInfo = updateAndWaitForComplete(stub, jobRun);
                if (statusInfo != null) {
                    jobRunStatus = statusInfo.getStatus();
                }
            }
        } catch (Exception e) {
            MetadataFailure.rethrowIfUnavailable(e);
            log.error("Exception found when executing jobRun: {} and wait for complete", jobRunId, e);
            completeWithError(jobRun, e);
            jobRunStatus = jobRun == null ? ERROR : jobRun.getStatus();
        }

        return false;
    }

    private RetryAndRun getRetryAndLatestRun() {
        var job = jobInfoService.getById(jobId);
        var config = job.getConfig();

        Long jobRunId = null;
        ExecutionStatus jobRunStatus = null;
        var jobRuns = jobRunInfoService.listLiteRunsLatestFirst(jobId, flowRunId);
        if (CollectionUtils.isNotEmpty(jobRuns)) {
            var latest = jobRuns.getFirst();
            jobRunId = latest.getId();
            jobRunStatus = latest.getStatus();
        }

        return new RetryAndRun(
                config.getRetryTimes(), config.parseRetryInterval(), jobRuns.size() - 1, jobRunId, jobRunStatus);
    }

    private void sleepRetry(@Nullable Duration interval) {
        if (interval == null || !interval.isPositive()) {
            ThreadUtil.sleep(THREE_SECOND_MILLIS);
            return;
        }

        var remaining = interval.toMillis();
        while (AppRunner.isRunning() && remaining > 0) {
            if (checkFlowAction() != FlowAction.PROCEED) {
                return;
            }

            var tmp = remaining;
            remaining = remaining - THREE_SECOND_MILLIS;
            ThreadUtil.sleep(remaining > 0 ? THREE_SECOND_MILLIS : tmp);
        }
    }

    private @Nullable JobRunInfo getOrCreateJobRun(JobInfo jobInfo) {
        var jobRun = jobRunInfoService.findCreatedRun(jobInfo.getId(), flowRunId);
        if (jobRun != null) {
            return jobRun;
        }

        return switch (jobRunExtraService.createJobRunIfOwned(jobInfo, flowRunId, HOST_IP)) {
            case CreateResult.Created created -> jobRunInfoService.getById(created.jobRunId());
            case CreateResult.Skipped skipped -> {
                log.info("Job run not created for flow run {}, job {}: {}", flowRunId, jobId, skipped.reason());
                yield null;
            }
        };
    }

    private JobRunInfo processRemoteJob(JobGrpcServiceBlockingStub stub, Long jobRunId) {
        var request = ProcessJobRequest.newBuilder().setJobRunId(jobRunId);
        var reply = stub.processJob(request.build());
        return jobRunInfoService.getById(reply.getJobRunId());
    }

    // Deliberately no ownership/kill check: a DB read per waiting job every 5s isn't worth it for the rare,
    // reassignment case — the new owner re-adopts the same jobRun and both converge at the remote job's terminal state.
    @Nullable
    private StatusInfo updateAndWaitForComplete(JobGrpcServiceBlockingStub stub, JobRunInfo jobRun) {
        while (AppRunner.isRunning() && !localRunAborted.getAsBoolean()) {
            try {
                var request = buildJobStatusRequest(jobRun);
                var statusReply = stub.getJobStatus(request);
                var statusInfo = StatusInfo.fromReplay(statusReply);
                log.info(
                        "Job runId: {}, name: {} Status: {}", jobRun.getId(), jobRun.getName(), statusInfo.getStatus());

                updateJobRunStatus(jobRun, statusInfo);
                var status = jobRun.getStatus();
                if (status.isTerminalState()) {
                    return new StatusInfo(status, statusInfo.getStartTime(), statusInfo.getEndTime());
                }
            } catch (Exception e) {
                MetadataFailure.rethrowIfUnavailable(e);
                log.error("Fetch or update job status failed, runId: {}", jobRun.getId(), e);
            }

            ThreadUtil.sleep(FIVE_SECOND_MILLIS);
        }

        return null;
    }

    private JobStatusRequest buildJobStatusRequest(JobRunInfo jobRun) {
        return JobStatusRequest.newBuilder()
                .setJobRunId(jobRun.getId())
                .setJobId(jobRun.getJobId())
                .setDeployMode(jobRun.getDeployMode().name())
                .setType(jobRun.getType().name())
                .build();
    }

    /**
     * Uses the transition result as the committed status.
     */
    private void updateJobRunStatus(JobRunInfo jobRun, StatusInfo statusInfo) {
        var status = statusInfo.getStatus();
        if (jobRun.getStatus() == status) {
            return;
        }

        var update = new JobRunInfo();
        if (status.isTerminalState()) {
            update.setEndTime(statusInfo.getEndTimeOrNow());
        }

        var transition = jobRunStateService.updateFromReport(jobRun.getId(), status, update);
        if (transition.status() != null) {
            jobRun.setStatus(transition.status());
        }
    }

    private void completeWithError(@Nullable JobRunInfo jobRun, Exception exception) {
        if (jobRun == null || jobRun.getStatus() == ERROR) {
            return;
        }

        try {
            var update = new JobRunInfo();
            update.setEndTime(LocalDateTime.now());
            update.setBackInfo(new JobCallback(ExceptionUtil.stackTrace(exception), null));
            var transition = jobRunStateService.completeWithError(jobRun.getId(), update);
            // update cache.
            if (transition.outcome() == APPLIED) {
                jobRun.setStatus(transition.status());
            }
        } catch (Exception e) {
            log.error("Update job run status failed", e);
        }
    }

    private JobResponse response(ExecutionStatus status) {
        return new JobResponse(jobId, jobRunId, status);
    }

    private JobResponse stopResponse(FlowAction flowAction) {
        return flowAction == FlowAction.TERMINATE ? response(KILLED) : JobResponse.ABORTED;
    }

    private boolean noRunInProgress() {
        return jobRunStatus == null || jobRunStatus.isTerminalState();
    }

    // The ERROR fallback is effectively unreachable — it exists solely to satisfy null-safety analysis.
    private ExecutionStatus jobRunStatusOrError() {
        return jobRunStatus != null ? jobRunStatus : ERROR;
    }

    private FlowAction checkFlowAction() {
        var flowRun = flowRunService.getLiteByIdOrNull(flowRunId);
        if (flowRun == null) {
            return FlowAction.PROCEED;
        }

        var host = flowRun.getHost();
        if (host != null && !HOST_IP.equals(host)) {
            return FlowAction.ABANDON;
        }

        var flowStatus = flowRun.getStatus();
        if (KILLING.equals(flowStatus) || flowStatus.isTerminalState()) {
            return FlowAction.TERMINATE;
        }

        return FlowAction.PROCEED;
    }

    /** What a job thread should do based on the owning flow run's current state. */
    private enum FlowAction {
        /** Flow still owned locally and running — keep executing. */
        PROCEED,

        /** Flow reassigned to another worker — leave the job non-terminal (returns {@link JobResponse#ABORTED}). */
        ABANDON,

        /** Flow finished or being killed — stop and report the job as {@code KILLED}. */
        TERMINATE
    }

    private record RetryAndRun(
            int retryTimes,
            Duration retryInterval,
            int retryAttempt,
            @Nullable Long jobRunId,
            @Nullable ExecutionStatus jobRunStatus) {}
}
