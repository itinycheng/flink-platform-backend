package com.flink.platform.common.enums;

import lombok.Getter;

/** execution mode. */
@Getter
public enum ExecutionMode {

    /** runtime execution mode. */
    STREAMING,

    BATCH
}
