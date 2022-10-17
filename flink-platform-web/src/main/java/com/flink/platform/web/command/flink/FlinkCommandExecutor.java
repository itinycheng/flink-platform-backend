package com.flink.platform.web.command.flink;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.command.CommandExecutor;
import com.flink.platform.web.command.JobCallback;
import com.flink.platform.web.command.JobCommand;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.external.YarnClientService;
import com.flink.platform.web.util.CommandCallback;
import com.flink.platform.web.util.CommandUtil;
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
import static com.flink.platform.web.util.CommandCallback.EXIT_CODE_SUCCESS;
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
        CommandCallback callback =
                CommandUtil.exec(
                        command.toCommandString(),
                        buildEnvProps(),
                        workerConfig.getFlinkSubmitTimeoutMills());

        String appId = extractApplicationId(callback.getStdMessage());
        String jobId = extractJobId(callback.getStdMessage());

        // call `killCommand` method if execute command failed.
        if (!callback.getExitCode().equals(EXIT_CODE_SUCCESS)) {
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
            return new JobCallback(jobId, appId, trackingUrl, callback, EMPTY, status);
        } else {
            String message =
                    String.join(LINE_SEPARATOR, callback.getStdMessage(), callback.getErrMessage());
            return new JobCallback(jobId, appId, EMPTY, callback, message, FAILURE);
        }
    }

    @Override
    public void killCommand(long jobRunId, JobCallback jobCallback) {
        // Kill shell command.
        CommandCallback cmdCallback = jobCallback.getCmdCallback();
        Integer processId = cmdCallback != null ? cmdCallback.getProcessId() : null;
        if (processId != null && processId > 0) {
            forceKill(processId, buildEnvProps());
        }

        // kill application.
        String appId = jobCallback.getAppId();
        if (StringUtils.isEmpty(appId)) {
            return;
        }

        JobRunInfo jobRun = jobRunInfoService.getById(jobRunId);
        if (FLINK_YARN_PER.equals(jobRun.getDeployMode())) {
            try {
                yarnClientService.killApplication(appId);
            } catch (Exception e) {
                log.error("Kill yarn application: {} failed", appId, e);
            }
        } else {
            log.warn(
                    "Kill command unsupported, applicationId: {}, deployMode: {}",
                    appId,
                    jobRun.getDeployMode());
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
