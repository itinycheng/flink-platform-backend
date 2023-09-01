package com.flink.platform.common.util;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Exception util. */
public class ExceptionUtil {

    public static String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer, true));
        return writer.toString();
    }
}
