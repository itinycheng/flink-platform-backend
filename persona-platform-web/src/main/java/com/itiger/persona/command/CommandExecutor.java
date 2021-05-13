package com.itiger.persona.command;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;

import static com.itiger.persona.common.constants.JobConstant.APP_ID_PATTERN;
import static com.itiger.persona.common.constants.JobConstant.JOB_ID_PATTERN;
import static com.itiger.persona.common.constants.JobConstant.LINE_SEPARATOR;
import static java.util.stream.Collectors.joining;

/**
 * @author tiny.wang
 */
@Slf4j
@Component("commandExecutor")
public class CommandExecutor {

    @Value("${hadoop.user}")
    private String hadoopUser;

    public JobCallback execCommand(String command) throws Exception {
        log.info("exec command: {}", command);
        Process process = Runtime.getRuntime().exec(command,
                new String[]{String.format("HADOOP_USER_NAME=%s", hadoopUser)});
        process.waitFor();
        try (BufferedReader stdReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
             BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String stdMsg = stdReader.lines().collect(joining(LINE_SEPARATOR));
            String errMsg = errReader.lines().collect(joining(LINE_SEPARATOR));
            String appId = extractApplicationId(stdMsg);
            String jobId = extractJobId(stdMsg);
            String message = String.join(LINE_SEPARATOR, stdMsg, errMsg);
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
