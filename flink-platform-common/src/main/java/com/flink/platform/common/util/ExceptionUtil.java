package com.flink.platform.common.util;

import lombok.extern.slf4j.Slf4j;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Exception util. */
@Slf4j
public class ExceptionUtil {

    public static String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer, true));
        return writer.toString();
    }

    public static void runWithErrorLogging(ThrowingRunnable... tasks) {
        runWithErrorLogging("Unexpected error occurred.", tasks);
    }

    public static void runWithErrorLogging(String errMsg, ThrowingRunnable... tasks) {
        for (ThrowingRunnable runnable : tasks) {
            try {
                runnable.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable throwable) {
                log.error(errMsg, throwable);
            }
        }
    }

    @FunctionalInterface
    public interface ThrowingRunnable {

        void run() throws Exception;
    }
}
