package com.flink.platform.common.enums;

/**
 * flow run strategy.
 */
public enum ExecutionStrategy {
    ONLY_CUR_JOB,

    ALL_POST_JOBS,

    ALL_PRE_JOBS,
}
