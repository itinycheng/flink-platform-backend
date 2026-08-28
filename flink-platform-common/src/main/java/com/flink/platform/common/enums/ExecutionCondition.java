package com.flink.platform.common.enums;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * TODO: rename to TriggerRule.
 */
public enum ExecutionCondition {

    /** All upstreams ended in the status expected by their edge. (was {@code AND}) */
    @JsonAlias("AND")
    ALL_MATCHED,

    /** At least one upstream matched its edge's expected status. (was {@code OR}) */
    @JsonAlias("OR")
    ANY_MATCHED,

    /** All upstreams reached a terminal state, regardless of the edge's expected status. */
    ALL_DONE,

    /** At least one upstream reached a terminal state, regardless of the edge's expected status. */
    ANY_DONE;

    public boolean requiresAllQualified() {
        switch (this) {
            case ALL_MATCHED:
            case ALL_DONE:
                return true;
            case ANY_MATCHED:
            case ANY_DONE:
                return false;
            default:
                throw new IllegalStateException("Unhandled execution condition: " + this);
        }
    }

    public boolean requiresStatusMatched() {
        switch (this) {
            case ALL_MATCHED:
            case ANY_MATCHED:
                return true;
            case ALL_DONE:
            case ANY_DONE:
                return false;
            default:
                throw new IllegalStateException("Unhandled execution condition: " + this);
        }
    }
}
