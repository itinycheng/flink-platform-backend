package com.flink.platform.web.jobrun;

import com.flink.platform.common.enums.ExecutionStatus;
import org.jspecify.annotations.Nullable;

/**
 * The status is the committed status: the target when applied, and the observed row status when
 * rejected. It is null when the row does not exist or could not be read.
 */
public record JobRunTransition(Outcome outcome, @Nullable ExecutionStatus status) {

    public enum Outcome {
        APPLIED,
        REJECTED,
        NOT_FOUND
    }

    public static JobRunTransition applied(ExecutionStatus status) {
        return new JobRunTransition(Outcome.APPLIED, status);
    }

    public static JobRunTransition rejected(ExecutionStatus status) {
        return new JobRunTransition(Outcome.REJECTED, status);
    }

    public static JobRunTransition notFound() {
        return new JobRunTransition(Outcome.NOT_FOUND, null);
    }
}
