package com.flink.platform.common.enums;

/** Permission enumeration. */
public enum Permission {
    // System level
    SYSTEM_MANAGE,

    // Workspace level
    WORKSPACE_MANAGE,
    WORKSPACE_VIEW,

    // Task level
    TASK_EDIT,
    TASK_EXEC,
    TASK_VIEW,
    TASK_PURGE
}
