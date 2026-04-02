package com.flink.platform.common.enums;

import lombok.Getter;

/** operation type for audit log. */
@Getter
public enum OperationType {
    INSERT,
    UPDATE,
    DELETE
}
