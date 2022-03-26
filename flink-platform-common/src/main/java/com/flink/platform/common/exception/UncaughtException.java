package com.flink.platform.common.exception;

/** For internal use, throw exceptions to restTemplate client side. */
public class UncaughtException extends RuntimeException {

    public UncaughtException(String message) {
        super(message);
    }

    public UncaughtException(String message, Throwable cause) {
        super(message, cause);
    }
}
