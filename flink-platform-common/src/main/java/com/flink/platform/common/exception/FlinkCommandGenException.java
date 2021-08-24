package com.flink.platform.common.exception;

/**
 * flink command generate exception
 *
 * @author tiny.wang
 */
public class FlinkCommandGenException extends RuntimeException {

    public FlinkCommandGenException(String message) {
        super(message);
    }

    public FlinkCommandGenException(String message, Throwable cause) {
        super(message, cause);
    }

}
