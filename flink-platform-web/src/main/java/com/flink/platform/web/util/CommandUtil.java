package com.flink.platform.web.util;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static com.flink.platform.common.constants.Constant.LINE_SEPARATOR;
import static java.util.stream.Collectors.joining;

/** Command util. */
@Slf4j
public class CommandUtil {

    public static CommandCallback exec(String command, String[] envProps)
            throws IOException, InterruptedException {
        log.info("exec command: {}", command);
        Process process = Runtime.getRuntime().exec(command, envProps);
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
            return new CommandCallback(stdMsg, errMsg);
        } finally {
            process.destroy();
        }
    }
}
