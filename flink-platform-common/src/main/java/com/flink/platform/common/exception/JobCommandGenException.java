package com.flink.platform.common.exception;

/** flink command generate exception. */
public class JobCommandGenException extends RuntimeException {

    public JobCommandGenException(String message) {
        super(message);
    }

    public JobCommandGenException(String message, Throwable cause) {
        super(message, cause);
    }
}
