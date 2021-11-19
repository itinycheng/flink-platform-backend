package com.flink.platform.common.enums;

import lombok.Getter;

/** Flow job status. */
@Getter
public enum JobFlowStatus {
    DELETE(-1, false),

    OFFLINE(0, false),

    ONLINE(1, true),

    SCHEDULING(2, true);

    private final int code;

    private final boolean runnable;

    JobFlowStatus(int code, boolean runnable) {
        this.code = code;
        this.runnable = runnable;
    }
}
