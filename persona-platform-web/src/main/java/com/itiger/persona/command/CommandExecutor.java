package com.itiger.persona.command;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static com.itiger.persona.command.Constants.LINE_SEPARATOR;
import static java.util.stream.Collectors.joining;

/**
 * @author tiny.wang
 */
@Slf4j
public class CommandExecutor {

    public static String execCommand(String command) throws Exception {
        log.info("exec command: {}", command);
        Process process = Runtime.getRuntime().exec(command);
        process.waitFor();
        try (BufferedReader stdReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
             BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String stdMsg = stdReader.lines().collect(joining(LINE_SEPARATOR));
            String errMsg = errReader.lines().collect(joining(LINE_SEPARATOR));
            return String.join(LINE_SEPARATOR, stdMsg, errMsg);
        } finally {
            process.destroy();
        }
    }

}
