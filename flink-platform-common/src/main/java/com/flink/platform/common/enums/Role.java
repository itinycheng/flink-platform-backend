package com.flink.platform.common.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static com.flink.platform.common.enums.Permission.SYSTEM_MANAGE;
import static com.flink.platform.common.enums.Permission.TASK_EDIT;
import static com.flink.platform.common.enums.Permission.TASK_EXEC;
import static com.flink.platform.common.enums.Permission.TASK_VIEW;
import static com.flink.platform.common.enums.Permission.WORKSPACE_MANAGE;
import static com.flink.platform.common.enums.Permission.WORKSPACE_VIEW;

/** Role enumeration with permission mapping. */
public enum Role {
    SUPER_ADMIN(EnumSet.of(SYSTEM_MANAGE, WORKSPACE_MANAGE, WORKSPACE_VIEW, TASK_VIEW)),
    ADMIN(EnumSet.of(WORKSPACE_MANAGE, WORKSPACE_VIEW, TASK_EDIT, TASK_EXEC, TASK_VIEW)),
    DEVELOPER(EnumSet.of(WORKSPACE_VIEW, TASK_EDIT, TASK_EXEC, TASK_VIEW)),
    OPERATOR(EnumSet.of(WORKSPACE_VIEW, TASK_EXEC, TASK_VIEW)),
    VIEWER(EnumSet.of(WORKSPACE_VIEW, TASK_VIEW));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }
}
