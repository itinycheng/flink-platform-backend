package com.flink.platform.web.util;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

/** Thread utils. */
public class ThreadUtil {

    public static final int MIN_SLEEP_TIME_MILLIS = 3000;

    public static final int MAX_SLEEP_TIME_MILLIS = 60_000;

    public static ThreadPoolExecutor newFixedThreadExecutor(String namePrefix, int threadsNum) {
        ThreadFactory threadFactory = namedThreadFactory(namePrefix, false);
        return (ThreadPoolExecutor) Executors.newFixedThreadPool(threadsNum, threadFactory);
    }

    public static ThreadPoolExecutor newFixedVirtualThreadExecutor(String namePrefix, int threadsNum) {
        var factory = Thread.ofVirtual().name(namePrefix + "-", 1).factory();
        return (ThreadPoolExecutor) Executors.newFixedThreadPool(threadsNum, factory);
    }

    public static ThreadPoolExecutor newDaemonFixedThreadExecutor(String namePrefix, int threadsNum) {
        ThreadFactory threadFactory = namedThreadFactory(namePrefix, true);
        return (ThreadPoolExecutor) Executors.newFixedThreadPool(threadsNum, threadFactory);
    }

    public static ScheduledExecutorService newDaemonSingleScheduledExecutor(String namePrefix) {
        ThreadFactory threadFactory = namedThreadFactory(namePrefix, true);
        return Executors.newSingleThreadScheduledExecutor(threadFactory);
    }

    public static ThreadFactory namedThreadFactory(String prefix, boolean isDaemon) {
        return new ThreadFactoryBuilder()
                .setDaemon(isDaemon)
                .setNameFormat(prefix + "-%d")
                .build();
    }

    public static void sleepRetry(int retryAttempt) {
        int mills = Math.min(retryAttempt * MIN_SLEEP_TIME_MILLIS, MAX_SLEEP_TIME_MILLIS);
        mills = Math.max(mills, MIN_SLEEP_TIME_MILLIS);
        sleep(mills);
    }

    public static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final Exception ignored) {
        }
    }

    public static void sleepDuration(int retryAttempt, final Duration duration) {
        if (duration == null || duration.isZero()) {
            sleepRetry(retryAttempt);
        } else {
            sleep(duration.toMillis());
        }
    }
}
