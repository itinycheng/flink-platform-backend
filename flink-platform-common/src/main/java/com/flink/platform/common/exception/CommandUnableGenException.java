package com.flink.platform.common.exception;

/** Command cannot be generated. */
public class CommandUnableGenException extends UnrecoverableException {
    public CommandUnableGenException(String message) {
        super(message);
    }
}
