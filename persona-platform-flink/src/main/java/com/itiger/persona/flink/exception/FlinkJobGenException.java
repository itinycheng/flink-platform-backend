package com.itiger.persona.flink.exception;

/**
 * gen flink job exception
 *
 * @author tiny.wang
 */
public class FlinkJobGenException extends RuntimeException {

    public FlinkJobGenException(String message) {
        super(message);
    }

    public FlinkJobGenException(String message, Throwable cause) {
        super(message, cause);
    }

}
