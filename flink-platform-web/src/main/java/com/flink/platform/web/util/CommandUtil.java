package com.flink.platform.web.util;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static com.flink.platform.common.constants.Constant.LINE_SEPARATOR;
import static com.flink.platform.web.util.CommandUtil.CmdOutType.ERR;
import static com.flink.platform.web.util.CommandUtil.CmdOutType.STD;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Command util. <br>
 * TODO: get processId, exitValue and save log to HDFS.
 */
@Slf4j
public class CommandUtil {

    private static final int MAX_LOG_ROWS = 50000;

    public static CommandCallback exec(String command, String[] envProps, long timeoutMills)
            throws IOException, InterruptedException {
        List<String> stdList = Collections.synchronizedList(new ArrayList<>());
        List<String> errList = Collections.synchronizedList(new ArrayList<>());

        boolean success =
                exec(
                        command,
                        envProps,
                        timeoutMills,
                        (inputType, value) -> {
                            switch (inputType) {
                                case STD:
                                    if (stdList.size() <= MAX_LOG_ROWS) {
                                        stdList.add(value);
                                    }
                                    break;
                                case ERR:
                                    if (errList.size() <= MAX_LOG_ROWS) {
                                        errList.add(value);
                                    }
                                    break;
                                default:
                                    log.error("unknown command log type: {}", inputType);
                            }
                        });

        return new CommandCallback(
                success,
                String.join(LINE_SEPARATOR, stdList),
                String.join(LINE_SEPARATOR, errList));
    }

    public static boolean exec(
            String command,
            String[] envProps,
            long timeoutMills,
            BiConsumer<CmdOutType, String> logConsumer)
            throws IOException, InterruptedException {
        log.info("Exec command: {}, env properties: {}", command, envProps);
        Process process = Runtime.getRuntime().exec(command, envProps);

        try (InputStream stdStream = process.getInputStream();
                InputStream errStream = process.getErrorStream()) {
            CollectLogThread stdThread = new CollectLogThread(stdStream, STD, logConsumer);
            CollectLogThread errThread = new CollectLogThread(errStream, ERR, logConsumer);

            try {
                stdThread.start();
                errThread.start();
            } catch (Exception e) {
                log.error("Start log collection thread failed", e);
            }

            boolean status = process.waitFor(timeoutMills, MILLISECONDS);

            try {
                stdThread.join();
                errThread.join();
            } catch (Exception e) {
                log.error("join log collection thread failed", e);
            }

            return status;
        } finally {
            process.destroy();
        }
    }

    static class CollectLogThread extends Thread {

        private static final AtomicInteger COUNTER = new AtomicInteger(0);

        private final InputStream inputStream;

        private final CmdOutType inputType;

        private final BiConsumer<CmdOutType, String> consumer;

        public CollectLogThread(
                InputStream inputStream,
                CmdOutType inputType,
                BiConsumer<CmdOutType, String> consumer) {
            super("collect-stream-log-" + COUNTER.incrementAndGet());
            this.inputStream = inputStream;
            this.inputType = inputType;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            try {
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                reader.lines().forEach(line -> consumer.accept(inputType, line));
            } catch (Exception e) {
                log.error("Error found while reading from stream", e);
            }
        }
    }

    /** command output type. */
    public enum CmdOutType {
        STD,
        ERR
    }
}
