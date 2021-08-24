package com.flink.platform.common.enums;

import lombok.Getter;

/**
 * @author tiny.wang
 */
@Getter
public enum ExecutionMode {

    /**
     * runtime execution mode
     */
    STREAMING,

    BATCH
}
