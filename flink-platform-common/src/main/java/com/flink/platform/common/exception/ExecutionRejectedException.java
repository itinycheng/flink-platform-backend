package com.flink.platform.common.exception;

import lombok.Getter;

/**
 * A flow or job execution request is deterministically rejected by its definition or current
 * persisted state. The same unchanged request should not be retried.
 */
@Getter
public class ExecutionRejectedException extends RuntimeException {

    private final Reason reason;

    public ExecutionRejectedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ExecutionRejectedException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public enum Reason {
        MISSING_STATE,
        INVALID_STATE,
        INVALID_DEFINITION,
        UNSUPPORTED_TYPE
    }
}
