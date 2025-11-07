package com.flink.platform.common.enums;

import lombok.Getter;

/** Flow job status. */
@Getter
public enum JobFlowType {
    JOB_FLOW,
    JOB_LIST,
    SUB_FLOW;

    public boolean supportsCron() {
        return this == JOB_FLOW;
    }
}
