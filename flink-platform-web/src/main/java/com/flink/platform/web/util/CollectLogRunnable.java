package com.flink.platform.web.util;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/** Collect exec command log. */
@Slf4j
public class CollectLogRunnable implements Runnable {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    @Getter
    private final String name;

    private final InputStream inputStream;

    private final CmdOutType inputType;

    private final BiConsumer<CmdOutType, String> consumer;

    public CollectLogRunnable(InputStream inputStream, CmdOutType inputType, BiConsumer<CmdOutType, String> consumer) {
        this.name = "collect-stream-log-" + COUNTER.incrementAndGet();
        this.inputStream = inputStream;
        this.inputType = inputType;
        this.consumer = consumer;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                consumer.accept(inputType, line);
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Error found while reading from stream", e);
        }
    }

    /** Log output channel. */
    public enum CmdOutType {
        STD,
        ERR
    }
}
