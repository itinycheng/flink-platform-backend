package com.flink.platform.common.enums;

/**
 * Worker status.
 * Check INACTIVE workers during heartbeat.
 */
public enum WorkerStatus {
    @Deprecated
    LEADER,
    @Deprecated
    FOLLOWER,
    ACTIVE,
    INACTIVE,
    DELETED
}
