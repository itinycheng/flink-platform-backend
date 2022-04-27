package com.flink.platform.web.util;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static com.flink.platform.common.constants.Constant.LINE_SEPARATOR;
import static com.flink.platform.web.util.CommandUtil.CmdLogType.ERR;
import static com.flink.platform.web.util.CommandUtil.CmdLogType.STD;

/** Command util. */
@Slf4j
public class CommandUtil {

    public static CommandCallback exec(String command, String[] envProps)
            throws IOException, InterruptedException {
        List<String> stdList = new ArrayList<>();
        List<String> errList = new ArrayList<>();

        exec(
                command,
                envProps,
                (inputType, value) -> {
                    switch (inputType) {
                        case STD:
                            stdList.add(value);
                            break;
                        case ERR:
                            errList.add(value);
                            break;
                        default:
                            log.error("unknown command log type: {}", inputType);
                    }
                });

        return new CommandCallback(
                String.join(LINE_SEPARATOR, stdList), String.join(LINE_SEPARATOR, errList));
    }

    public static void exec(
            String command, String[] envProps, BiConsumer<CmdLogType, String> logConsumer)
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

            process.waitFor();

            try {
                stdThread.join();
                errThread.join();
            } catch (Exception e) {
                log.error("join log collection thread failed", e);
            }
        } finally {
            process.destroy();
        }
    }

    static class CollectLogThread extends Thread {

        private static final AtomicInteger COUNTER = new AtomicInteger(0);

        private final InputStream inputStream;

        private final CmdLogType inputType;

        private final BiConsumer<CmdLogType, String> consumer;

        public CollectLogThread(
                InputStream inputStream,
                CmdLogType inputType,
                BiConsumer<CmdLogType, String> consumer) {
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

    enum CmdLogType {
        STD,
        ERR
    }
}
