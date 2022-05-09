package com.flink.platform.web.command;

import com.flink.platform.web.util.CommandCallback;
import com.flink.platform.web.util.CommandUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;

import static com.flink.platform.common.constants.Constant.LINE_SEPARATOR;
import static com.flink.platform.common.constants.JobConstant.APP_ID_PATTERN;
import static com.flink.platform.common.constants.JobConstant.HADOOP_USER_NAME;
import static com.flink.platform.common.constants.JobConstant.JOB_ID_PATTERN;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;

/** Flink command executor. */
@Slf4j
@Component("flinkCommandExecutor")
public class FlinkCommandExecutor implements CommandExecutor {

    @Value("${hadoop.username}")
    private String hadoopUser;

    @Override
    public boolean isSupported(JobCommand jobCommand) {
        return jobCommand instanceof FlinkCommand;
    }

    @Override
    public JobCallback execCommand(JobCommand command) throws Exception {
        CommandCallback callback =
                CommandUtil.exec(
                        command.toCommandString(),
                        new String[] {String.format("%s=%s", HADOOP_USER_NAME, hadoopUser)});

        String appId = extractApplicationId(callback.getStdMessage());
        String jobId = extractJobId(callback.getStdMessage());
        String message = StringUtils.EMPTY;
        if (StringUtils.isEmpty(appId) || StringUtils.isEmpty(jobId)) {
            message =
                    String.join(LINE_SEPARATOR, callback.getStdMessage(), callback.getErrMessage());
        }

        boolean isSucceed = StringUtils.isNotEmpty(appId);
        return new JobCallback(jobId, appId, message, isSucceed ? SUCCESS : FAILURE);
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
