package com.flink.platform.common.enums;

/**
 * Worker status.
 * Check INACTIVE workers during heartbeat.
 */
public enum WorkerStatus {
    ACTIVE,
    INACTIVE,
    DELETED
}
