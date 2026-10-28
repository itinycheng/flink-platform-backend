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
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.grpc.JobGrpcClient;
import com.flink.platform.web.lifecycle.AppRunner;
import com.flink.platform.web.monitor.StatusInfo;
import com.flink.platform.web.service.JobRunExtraService;
import com.flink.platform.web.service.JobRunExtraService.CreateResult;
import com.flink.platform.web.util.ThreadUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static com.flink.platform.common.constants.Constant.HOST_IP;
import static com.flink.platform.common.enums.ExecutionStatus.CREATED;
import static com.flink.platform.common.enums.ExecutionStatus.ERROR;
import static com.flink.platform.common.enums.ExecutionStatus.KILLED;
import static com.flink.platform.common.enums.ExecutionStatus.KILLING;
import static com.flink.platform.common.enums.ExecutionStatus.RUNNING;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static com.flink.platform.common.enums.ExecutionStatus.WAITING;
import static com.flink.platform.grpc.JobGrpcServiceGrpc.JobGrpcServiceBlockingStub;
import static com.flink.platform.web.util.ThreadUtil.FIVE_SECOND_MILLIS;
import static com.flink.platform.web.util.ThreadUtil.THREE_SECOND_MILLIS;
import static java.util.Objects.requireNonNull;

/** Execute job in a separate thread. */
@Slf4j
public class JobExecuteThread implements Supplier<JobResponse> {

    private final Long flowRunId;

    private final Long jobId;

    private final JobInfoService jobInfoService;

    private final JobRunInfoService jobRunInfoService;

    private final JobRunExtraService jobRunExtraService;

    private final JobFlowRunService flowRunService;

    private final JobGrpcClient jobGrpcClient;

    @Nullable
    private Long jobRunId;

    @Nullable
    private ExecutionStatus jobRunStatus;

    public JobExecuteThread(Long flowRunId, JobVertex jobVertex) {
        this.flowRunId = flowRunId;
        this.jobId = jobVertex.getJobId();
        this.jobInfoService = SpringContext.getBean(JobInfoService.class);
        this.jobRunInfoService = SpringContext.getBean(JobRunInfoService.class);
        this.jobRunExtraService = SpringContext.getBean(JobRunExtraService.class);
        this.flowRunService = SpringContext.getBean(JobFlowRunService.class);
        this.jobGrpcClient = SpringContext.getBean(JobGrpcClient.class);
    }

    @Override
    public JobResponse get() {
        RetryAndRun retryAndRun;
        try {
            retryAndRun = getRetryAndLatestRun();
        } catch (Exception e) {
            log.error("get retry and latest run failed", e);
            return response(ERROR);
        }

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

            callOnce();
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

    private void callOnce() {
        JobRunInfo jobRun = null;
        try {
            // Get job info, return if not found.
            var jobInfo = jobInfoService.findJobByIdAndStatus(jobId, JobStatus.ONLINE);
            if (jobInfo == null) {
                throw new RuntimeException("The job: %s is no longer exists or not in ONLINE status.".formatted(jobId));
            }

            // Get or create new jobRun.
            if (jobRunId == null || jobRunStatus == null || jobRunStatus.isTerminalState()) {
                jobRun = getOrCreateJobRun(jobInfo);
            } else {
                jobRun = jobRunInfoService.getById(jobRunId);
                log.info("Job:{} already submitted, runId = {}, status: {}.", jobId, jobRunId, jobRun.getStatus());
            }

            if (jobRun == null) {
                return;
            }

            // Update jobRunId and jobRunStatus in memory.
            jobRunId = jobRun.getId();
            jobRunStatus = jobRun.getStatus();
            if (jobRunStatus.isTerminalState()) {
                return;
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
            var statusInfo = new StatusInfo(ERROR, null, System.currentTimeMillis());
            updateJobRunIfNeeded(jobRun, statusInfo, StatusUpdate.LOCAL_ERROR, e);
            jobRunStatus = jobRun == null ? ERROR : jobRun.getStatus();
            log.error("Exception found when executing jobRun: {} and wait for complete", jobRunId, e);
        }
    }

    private RetryAndRun getRetryAndLatestRun() {
        int retry = 0;
        while (AppRunner.isRunning() && retry++ < 3) {
            try {
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
                        config.getRetryTimes(),
                        config.parseRetryInterval(),
                        jobRuns.size() - 1,
                        jobRunId,
                        jobRunStatus);
            } catch (Exception e) {
                log.error("Load retry config and latest run failed, jobId: {}, flowRunId: {}", jobId, flowRunId, e);
                ThreadUtil.sleep(1000);
            }
        }
        throw new IllegalStateException(
                "Load retry config and latest run failed, retry exhausted, jobId: %s, flowRunId: %s"
                        .formatted(jobId, flowRunId));
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
        while (AppRunner.isRunning()) {
            try {
                var request = buildJobStatusRequest(jobRun);
                var statusReply = stub.getJobStatus(request);
                var statusInfo = StatusInfo.fromReplay(statusReply);
                log.info(
                        "Job runId: {}, name: {} Status: {}", jobRun.getId(), jobRun.getName(), statusInfo.getStatus());
                updateJobRunIfNeeded(jobRun, statusInfo, StatusUpdate.WORKER_REPORTED, null);
                if (jobRun.getStatus().isTerminalState()) {
                    return new StatusInfo(jobRun.getStatus(), statusInfo.getStartTime(), statusInfo.getEndTime());
                }
            } catch (Exception e) {
                log.error("Fetch job status failed, runId: {}", jobRun.getId(), e);
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

    private void updateJobRunIfNeeded(
            @Nullable JobRunInfo jobRun, StatusInfo statusInfo, StatusUpdate update, @Nullable Exception exception) {
        try {
            var status = statusInfo.getStatus();
            if (jobRun == null || jobRun.getStatus() == status) {
                return;
            }

            var newRun = new JobRunInfo();
            newRun.setStatus(status);
            if (status.isTerminalState()) {
                newRun.setEndTime(statusInfo.getEndTimeOrNow());
            }
            if (exception != null) {
                var exceptionMsg = ExceptionUtil.stackTrace(exception);
                newRun.setBackInfo(new JobCallback(exceptionMsg, null));
            }

            if (jobRunInfoService.updateStatusFromAnyTo(jobRun.getId(), update.expectedStatuses, newRun) == 1) {
                jobRun.setStatus(status);
            } else {
                var current = jobRunInfoService.getLiteById(jobRun.getId());
                if (current != null) {
                    jobRun.setStatus(current.getStatus());
                }
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

    private enum StatusUpdate {
        /** Accept a worker report while the persisted run is still active. */
        WORKER_REPORTED(List.of(CREATED, WAITING, SUBMITTED, RUNNING)),

        /** A local exception may fail a run only before remote execution has been claimed. */
        LOCAL_ERROR(List.of(CREATED));

        private final List<ExecutionStatus> expectedStatuses;

        StatusUpdate(List<ExecutionStatus> expectedStatuses) {
            this.expectedStatuses = expectedStatuses;
        }
    }

    private record RetryAndRun(
            int retryTimes,
            Duration retryInterval,
            int retryAttempt,
            @Nullable Long jobRunId,
            @Nullable ExecutionStatus jobRunStatus) {}
}
