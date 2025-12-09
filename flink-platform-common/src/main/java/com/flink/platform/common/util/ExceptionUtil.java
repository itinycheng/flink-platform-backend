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

    public static void runWithErrorLogging(Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable throwable) {
            log.error("Unexpected error occurred.", throwable);
        }
    }
}
