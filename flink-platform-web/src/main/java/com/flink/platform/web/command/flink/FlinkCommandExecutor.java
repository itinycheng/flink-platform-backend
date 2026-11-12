package com.flink.platform.web.command.flink;

import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.result.JobCallback;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.command.JobCommand;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.environment.YarnAppService;
import com.flink.platform.web.util.YarnHelper;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

import static com.flink.platform.common.constants.Constant.EMPTY;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;

/** Flink command executor. */
@Slf4j
@Component("flinkCommandExecutor")
public class FlinkCommandExecutor implements CommandExecutor {

    private static final List<JobType> SUPPORTED_JOB_TYPES = Arrays.asList(JobType.FLINK_JAR, JobType.FLINK_SQL);

    @Autowired
    private WorkerConfig workerConfig;

    @Autowired
    private YarnAppService yarnAppService;

    @Autowired
    private JobRunInfoService jobRunInfoService;

    @Override
    public boolean isSupported(JobType jobType) {
        return SUPPORTED_JOB_TYPES.contains(jobType);
    }

    @Nonnull
    @Override
    public JobCallback execCommand(@Nonnull JobCommand command) throws Exception {
        var flinkCommand = (FlinkCommand) command;
        var task = new FlinkYarnTask(
                flinkCommand.getJobRunId(),
                flinkCommand.getMode(),
                flinkCommand.toCommandString(),
                null,
                workerConfig.getFlinkSubmissionTimeout(),
                yarnAppService);
        flinkCommand.setTask(task);
        task.run();

        var appId = task.getAppId();
        var jobId = task.getJobId();
        var callback = task.buildShellCallback();

        // No appId/jobId captured: nothing was submitted to YARN — use the shell's exit status as the final status.
        if (StringUtils.isEmpty(appId) && StringUtils.isEmpty(jobId)) {
            return new JobCallback(jobId, appId, EMPTY, callback, EMPTY, task.finalStatus());
        }

        // Get the application report from Hadoop Yarn.
        var status = SUBMITTED;
        var trackingUrl = EMPTY;
        try {
            var jobRunId = command.getJobRunId();
            var applicationTag = YarnHelper.getApplicationTag(jobRunId);
            var statusReport = yarnAppService.getStatusReportWithRetry(applicationTag);
            if (statusReport != null) {
                status = statusReport.getStatus();
                trackingUrl = statusReport.getTrackingUrl();
            }
        } catch (Exception e) {
            log.error("Failed to get ApplicationReport after command executed", e);
        }
        return new JobCallback(jobId, appId, trackingUrl, callback, EMPTY, status);
    }

    @Override
    public void killCommand(@Nonnull JobCommand command) {
        // Need provide processId and deployMode.
        var task = command.getTask();
        if (task == null) {
            var jobRun = jobRunInfoService.getById(command.getJobRunId());
            if (jobRun == null || jobRun.getStatus().isTerminalState()) {
                return;
            }

            var newTask = new FlinkYarnTask(jobRun.getId(), jobRun.getDeployMode(), yarnAppService);
            var callback = jobRun.getBackInfo();
            if (callback != null) {
                newTask.setProcessId(callback.getProcessId());
            }
            task = newTask;
        }

        task.cancel();
    }
}
