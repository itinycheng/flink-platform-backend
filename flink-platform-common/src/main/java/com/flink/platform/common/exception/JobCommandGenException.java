package com.flink.platform.common.exception;

/**
 * flink command generate exception
 *
 * @author tiny.wang
 */
public class JobCommandGenException extends RuntimeException {

    public JobCommandGenException(String message) {
        super(message);
    }

    public JobCommandGenException(String message, Throwable cause) {
        super(message, cause);
    }

}
