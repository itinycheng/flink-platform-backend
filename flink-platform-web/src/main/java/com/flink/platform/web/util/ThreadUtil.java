package com.flink.platform.web.util;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

/** Thread utils. */
public class ThreadUtil {

    public static ThreadPoolExecutor newFixedThreadExecutor(String namePrefix, int threadsNum) {
        ThreadFactory threadFactory = namedThreadFactory(namePrefix, false);
        return (ThreadPoolExecutor) Executors.newFixedThreadPool(threadsNum, threadFactory);
    }

    public static ThreadPoolExecutor newDaemonFixedThreadExecutor(
            String namePrefix, int threadsNum) {
        ThreadFactory threadFactory = namedThreadFactory(namePrefix, true);
        return (ThreadPoolExecutor) Executors.newFixedThreadPool(threadsNum, threadFactory);
    }

    public static ScheduledExecutorService newDaemonSingleScheduledExecutor(String namePrefix) {
        ThreadFactory threadFactory = namedThreadFactory(namePrefix, true);
        return Executors.newSingleThreadScheduledExecutor(threadFactory);
    }

    public static ThreadFactory namedThreadFactory(String prefix, boolean isDaemon) {
        return new ThreadFactoryBuilder().setDaemon(isDaemon).setNameFormat(prefix + "-%d").build();
    }

    public static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final Exception ignored) {
        }
    }
}
