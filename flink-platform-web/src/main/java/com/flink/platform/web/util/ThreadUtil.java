package com.flink.platform.web.util;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Thread utils. */
@Slf4j
public class ThreadUtil {

    public static final int ONE_SECOND_MILLIS = 1000;

    public static final int MIN_SLEEP_TIME_MILLIS = 3000;

    public static final int DEFAULT_SLEEP_TIME_MILLIS = 5000;

    public static ThreadPoolExecutor newFixedThreadExecutor(String namePrefix, int threadsNum) {
        var threadFactory = namedThreadFactory(namePrefix, false);
        return (ThreadPoolExecutor) Executors.newFixedThreadPool(threadsNum, threadFactory);
    }

    public static ExecutorService newVirtualThreadExecutor(String namePrefix) {
        var factory = Thread.ofVirtual().name(namePrefix + "-", 1).factory();
        return Executors.newThreadPerTaskExecutor(factory);
    }

    public static ThreadPoolExecutor newFixedVirtualThreadExecutor(String namePrefix, int threadsNum) {
        var factory = Thread.ofVirtual().name(namePrefix + "-", 1).factory();
        return (ThreadPoolExecutor) Executors.newFixedThreadPool(threadsNum, factory);
    }

    public static ThreadPoolExecutor newDaemonFixedThreadExecutor(String namePrefix, int threadsNum) {
        var threadFactory = namedThreadFactory(namePrefix, true);
        return (ThreadPoolExecutor) Executors.newFixedThreadPool(threadsNum, threadFactory);
    }

    public static ScheduledExecutorService newDaemonSingleScheduledExecutor(String namePrefix) {
        var threadFactory = namedThreadFactory(namePrefix, true);
        return Executors.newSingleThreadScheduledExecutor(threadFactory);
    }

    public static ThreadFactory namedThreadFactory(String prefix, boolean isDaemon) {
        return new ThreadFactoryBuilder()
                .setDaemon(isDaemon)
                .setNameFormat(prefix + "-%d")
                .build();
    }

    public static void addShutdownHook(ExecutorService service, String name) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                log.info("JVM is shutting down, closing thread pool {}", name);
                service.shutdown();
                if (!service.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.info("Force shutting down thread pool {}", name);
                    service.shutdownNow();
                }
            } catch (Exception exception) {
                log.error("Error while shutting down thread pool {}", name, exception);
                service.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }));
    }

    public static void safeSleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final Exception ignored) {
        }
    }

    public static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException exception) {
            log.error("Thread sleep error", exception);
            Thread.currentThread().interrupt();
        }
    }
}
