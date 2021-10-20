package com.flink.platform.web.comn;

/** Quartz exception. */
public class QuartzException extends RuntimeException {

    public QuartzException(String message) {
        super(message);
    }

    public QuartzException(String message, Throwable cause) {
        super(message, cause);
    }
}
