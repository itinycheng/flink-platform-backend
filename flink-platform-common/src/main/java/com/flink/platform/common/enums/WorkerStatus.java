package com.flink.platform.common.enums;

import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

/**
 * Worker status.
 * Check INACTIVE workers during heartbeat.
 */
public enum WorkerStatus {
    LEADER,
    FOLLOWER,
    INACTIVE,
    DELETED;

    public static final Set<WorkerStatus> ACTIVE_STATUSES =
            Stream.of(LEADER, FOLLOWER).collect(toSet());

    public static boolean isActiveStatus(WorkerStatus status) {
        return ACTIVE_STATUSES.contains(status);
    }
}
