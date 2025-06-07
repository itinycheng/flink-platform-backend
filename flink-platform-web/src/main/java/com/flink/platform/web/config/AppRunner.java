package com.flink.platform.web.config;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

/** Application runner. */
@Slf4j
public final class AppRunner {

    private static final AtomicBoolean STOP = new AtomicBoolean(false);

    public static boolean isStopped() {
        return STOP.get();
    }

    public static boolean isRunning() {
        return !STOP.get();
    }

    public static void stop() {
        STOP.set(true);
        log.info("Application is stopped...");
    }
}
