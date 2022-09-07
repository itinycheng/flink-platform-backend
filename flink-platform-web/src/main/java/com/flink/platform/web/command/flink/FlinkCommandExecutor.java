package com.flink.platform.web.command.flink;

import com.flink.platform.common.enums.ExecutionStatus;
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
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;

/** Flink command executor. */
@Slf4j
@Component("flinkCommandExecutor")
public class FlinkCommandExecutor implements CommandExecutor {

    @Value("${hadoop.username}")
    private String hadoopUser;

    @Autowired private WorkerConfig workerConfig;

    @Lazy @Autowired private YarnClientService yarnClientService;

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
                        new String[] {String.format("%s=%s", HADOOP_USER_NAME, hadoopUser)},
                        workerConfig.getFlinkSubmitTimeoutMills());

        String appId = extractApplicationId(callback.getStdMessage());
        String jobId = extractJobId(callback.getStdMessage());

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
            return new JobCallback(jobId, appId, trackingUrl, EMPTY, status);
        } else {
            String message =
                    String.join(LINE_SEPARATOR, callback.getStdMessage(), callback.getErrMessage());
            return new JobCallback(jobId, appId, EMPTY, message, FAILURE);
        }
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
