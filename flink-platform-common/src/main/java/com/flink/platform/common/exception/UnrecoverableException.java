package com.flink.platform.common.exception;

/** unrecoverable exception. */
public class UnrecoverableException extends RuntimeException {

    public UnrecoverableException(String message) {
        super(message);
    }

    public UnrecoverableException(String message, Throwable cause) {
        super(message, cause);
    }
}
