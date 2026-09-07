package com.flink.platform.common.enums;

/** operation type for audit log. */
public enum OperationType {
    INSERT,
    UPDATE,
    DELETE,
    SCHEDULE,
    UNSCHEDULE,
    RUN,
    KILL
}
