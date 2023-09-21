package com.flink.platform.web.command.flink;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.result.JobCallback;
import com.flink.platform.dao.entity.result.ShellCallback;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.command.AbstractTask;
import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.command.JobCommand;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.external.YarnClientService;
import com.flink.platform.web.util.YarnHelper;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;

import static com.flink.platform.common.constants.Constant.EMPTY;
import static com.flink.platform.common.constants.JobConstant.APP_ID_PATTERN;
import static com.flink.platform.common.constants.JobConstant.HADOOP_USER_NAME;
import static com.flink.platform.common.constants.JobConstant.JOB_ID_PATTERN;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;

/** Flink command executor. */
@Slf4j
@Component("flinkCommandExecutor")
public class FlinkCommandExecutor implements CommandExecutor {

    private static final List<JobType> SUPPORTED_JOB_TYPES = Arrays.asList(JobType.FLINK_JAR, JobType.FLINK_SQL);

    @Value("${hadoop.username}")
    private String hadoopUser;

    @Autowired
    private WorkerConfig workerConfig;

    @Lazy
    @Autowired
    private YarnClientService yarnClientService;

    @Autowired
    private JobRunInfoService jobRunInfoService;

    @Override
    public boolean isSupported(JobType jobType) {
        return SUPPORTED_JOB_TYPES.contains(jobType);
    }

    @Nonnull
    @Override
    public JobCallback execCommand(@Nonnull JobCommand command) throws Exception {
        FlinkCommand flinkCommand = (FlinkCommand) command;
        FlinkYarnTask task = new FlinkYarnTask(
                flinkCommand.getJobRunId(),
                flinkCommand.getMode(),
                flinkCommand.toCommandString(),
                buildEnvProps(),
                workerConfig.getFlinkSubmitTimeoutMills());
        flinkCommand.setTask(task);
        task.run();

        String appId = task.getAppId();
        String jobId = task.getJobId();
        ShellCallback callback = task.buildShellCallback();

        // call `killCommand` method if execute command failed.
        if (task.finalStatus() != SUCCESS) {
            return new JobCallback(jobId, appId, null, callback, EMPTY, task.finalStatus());
        }

        if (StringUtils.isNotEmpty(appId) && StringUtils.isNotEmpty(jobId)) {
            ExecutionStatus status = SUBMITTED;
            String trackingUrl = EMPTY;
            try {
                ApplicationReport applicationReport = yarnClientService.getApplicationReport(appId);
                status = YarnHelper.getStatus(applicationReport);
                trackingUrl = applicationReport.getTrackingUrl();
            } catch (Exception e) {
                log.error("Failed to get ApplicationReport after command executed", e);
            }
            return new JobCallback(jobId, appId, trackingUrl, callback, EMPTY, status);
        } else {
            return new JobCallback(jobId, appId, EMPTY, callback, EMPTY, FAILURE);
        }
    }

    @Override
    public void killCommand(@Nonnull JobCommand command) {
        // Need provide processId, applicationId, deployMode.
        AbstractTask task = command.getTask();
        if (task == null) {
            JobRunInfo jobRun = jobRunInfoService.getById(command.getJobRunId());
            JobCallback jobCallback = jobRun.getBackInfo();
            if (!jobRun.getStatus().isTerminalState() && jobCallback != null) {
                FlinkYarnTask newTask = new FlinkYarnTask(jobRun.getId(), jobRun.getDeployMode());
                newTask.setProcessId(jobCallback.getProcessId());
                newTask.setAppId(jobCallback.getAppId());
                task = newTask;
            }
        }

        if (task != null) {
            task.cancel();
        }
    }

    private String[] buildEnvProps() {
        return new String[] {String.format("%s=%s", HADOOP_USER_NAME, hadoopUser)};
    }

    // ------------------------------------------------------------------------
    //  exposed static methods for test cases
    // ------------------------------------------------------------------------

    public static String extractApplicationId(String message) {
        Matcher matcher = APP_ID_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return null;
        }
    }

    public static String extractJobId(String message) {
        Matcher matcher = JOB_ID_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return null;
        }
    }
}
