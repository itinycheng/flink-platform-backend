package com.flink.platform.web.command;

import com.flink.platform.web.enums.JobType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;

import static com.flink.platform.common.constants.Constant.LINE_SEPARATOR;
import static com.flink.platform.common.constants.JobConstant.APP_ID_PATTERN;
import static com.flink.platform.common.constants.JobConstant.HADOOP_USER_NAME;
import static com.flink.platform.common.constants.JobConstant.JOB_ID_PATTERN;
import static java.util.stream.Collectors.joining;

/** Flink command executor. */
@Slf4j
@Component("flinkCommandExecutor")
public class FlinkCommandExecutor implements CommandExecutor {

    private static final List<JobType> SUPPORTED_JOB_TYPES =
            Arrays.asList(JobType.FLINK_JAR, JobType.FLINK_SQL);

    @Value("${hadoop.user}")
    private String hadoopUser;

    @Override
    public boolean isSupported(JobType jobType) {
        return SUPPORTED_JOB_TYPES.contains(jobType);
    }

    @Override
    public JobCallback execCommand(String command) throws Exception {
        log.info("exec command: {}", command);
        Process process =
                Runtime.getRuntime()
                        .exec(
                                command,
                                new String[] {
                                    String.format("%s=%s", HADOOP_USER_NAME, hadoopUser)
                                });
        process.waitFor();
        try (BufferedReader stdReader =
                        new BufferedReader(
                                new InputStreamReader(
                                        process.getInputStream(), StandardCharsets.UTF_8));
                BufferedReader errReader =
                        new BufferedReader(
                                new InputStreamReader(
                                        process.getErrorStream(), StandardCharsets.UTF_8))) {
            String stdMsg = stdReader.lines().collect(joining(LINE_SEPARATOR));
            String errMsg = errReader.lines().collect(joining(LINE_SEPARATOR));
            String appId = extractApplicationId(stdMsg);
            String jobId = extractJobId(stdMsg);
            String message = StringUtils.EMPTY;
            if (StringUtils.isEmpty(appId) || StringUtils.isEmpty(jobId)) {
                message = String.join(LINE_SEPARATOR, stdMsg, errMsg);
            }
            return new JobCallback(jobId, appId, message);
        } finally {
            process.destroy();
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
