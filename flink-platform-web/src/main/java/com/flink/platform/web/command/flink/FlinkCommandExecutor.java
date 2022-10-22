package com.flink.platform.web.command.flink;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.util.JsonUtil;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.command.JobCallback;
import com.flink.platform.web.command.JobCommand;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.external.YarnClientService;
import com.flink.platform.web.util.ShellCallback;
import com.flink.platform.web.util.YarnHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

import java.util.regex.Matcher;

import static com.flink.platform.common.constants.Constant.EMPTY;
import static com.flink.platform.common.constants.Constant.LINE_SEPARATOR;
import static com.flink.platform.common.constants.JobConstant.APP_ID_PATTERN;
import static com.flink.platform.common.constants.JobConstant.HADOOP_USER_NAME;
import static com.flink.platform.common.constants.JobConstant.JOB_ID_PATTERN;
import static com.flink.platform.common.enums.DeployMode.FLINK_YARN_PER;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.KILLABLE;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static com.flink.platform.web.util.CommandUtil.EXIT_CODE_SUCCESS;
import static com.flink.platform.web.util.CommandUtil.forceKill;

/** Flink command executor. */
@Slf4j
@Component("flinkCommandExecutor")
public class FlinkCommandExecutor implements CommandExecutor {

    @Value("${hadoop.username}")
    private String hadoopUser;

    @Autowired private WorkerConfig workerConfig;

    @Lazy @Autowired private YarnClientService yarnClientService;

    @Autowired private JobRunInfoService jobRunInfoService;

    @Override
    public boolean isSupported(JobCommand jobCommand) {
        return jobCommand instanceof FlinkCommand;
    }

    @Nonnull
    @Override
    public JobCallback execCommand(JobCommand command) throws Exception {
        FlinkYarnTask task =
                new FlinkYarnTask(
                        command.getJobRunId(),
                        command.toCommandString(),
                        buildEnvProps(),
                        workerConfig.getFlinkSubmitTimeoutMills());
        command.setTask(task);
        task.run();

        String appId = task.getAppId();
        String jobId = task.getJobId();
        ShellCallback callback = task.buildShellCallback();

        // call `killCommand` method if execute command failed.
        if (task.getExitValue() != EXIT_CODE_SUCCESS) {
            return new JobCallback(jobId, appId, null, callback, EMPTY, KILLABLE);
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
            return new JobCallback(jobId, appId, trackingUrl, null, EMPTY, status);
        } else {
            String message =
                    String.join(LINE_SEPARATOR, callback.getStdMsg(), callback.getErrMsg());
            return new JobCallback(jobId, appId, EMPTY, callback, message, FAILURE);
        }
    }

    @Override
    public void killCommand(JobCommand command) {
        Integer processId = null;
        String applicationId = null;

        FlinkYarnTask task = command.getTask().unwrap(FlinkYarnTask.class);
        if (task != null) {
            processId = task.getProcessId();
            applicationId = task.getAppId();
        } else {
            JobRunInfo jobRun = jobRunInfoService.getById(command.getJobRunId());
            JobCallback jobCallback = JsonUtil.toBean(jobRun.getBackInfo(), JobCallback.class);
            if (jobCallback != null) {
                ShellCallback cmdCallback = jobCallback.getCmdCallback();
                processId = cmdCallback != null ? cmdCallback.getProcessId() : null;
                applicationId = jobCallback.getAppId();
            }
        }

        // Kill shell command.
        if (processId != null && processId > 0) {
            forceKill(processId, buildEnvProps());
        }

        // kill application.
        if (StringUtils.isNotEmpty(applicationId)) {
            JobRunInfo jobRun = jobRunInfoService.getById(command.getJobRunId());
            if (FLINK_YARN_PER.equals(jobRun.getDeployMode())) {
                try {
                    yarnClientService.killApplication(applicationId);
                } catch (Exception e) {
                    log.error("Kill yarn application: {} failed", applicationId, e);
                }
            } else {
                log.warn(
                        "Kill command unsupported, applicationId: {}, deployMode: {}",
                        applicationId,
                        jobRun.getDeployMode());
            }
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
