package com.flink.platform.common.enums;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * Trigger rule over a vertex's upstream edges: how upstream statuses gate whether this vertex runs.
 *
 * <p>TODO: rename to TriggerRule.
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
    ANY_DONE
}
