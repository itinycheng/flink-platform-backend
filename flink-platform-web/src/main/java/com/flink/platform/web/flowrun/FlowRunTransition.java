package com.flink.platform.web.flowrun;

import com.flink.platform.common.enums.ExecutionStatus;
import org.jspecify.annotations.Nullable;

public record FlowRunTransition(Outcome outcome, @Nullable ExecutionStatus status) {

    public enum Outcome {
        APPLIED,
        REJECTED,
        NOT_FOUND
    }

    public static FlowRunTransition applied(ExecutionStatus status) {
        return new FlowRunTransition(Outcome.APPLIED, status);
    }

    public static FlowRunTransition rejected(ExecutionStatus status) {
        return new FlowRunTransition(Outcome.REJECTED, status);
    }

    public static FlowRunTransition notFound() {
        return new FlowRunTransition(Outcome.NOT_FOUND, null);
    }
}
