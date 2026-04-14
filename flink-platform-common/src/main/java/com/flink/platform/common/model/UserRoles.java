package com.flink.platform.common.model;

import com.flink.platform.common.enums.Permission;
import com.flink.platform.common.enums.Role;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** User roles stored as JSON in t_user.roles. */
@Data
@NoArgsConstructor
public class UserRoles {

    /** System-level role. Null means no system permissions. Only SUPER_ADMIN is valid here. */
    private Role global;

    /**
     * Workspace-level roles. Key is workspace ID, value is the role within that workspace. Only
     * ADMIN/DEVELOPER/OPERATOR/VIEWER are valid values.
     */
    private Map<Long, Role> workspaces = Collections.emptyMap();

    public boolean hasGlobalPermission(Permission permission) {
        return global != null && global.hasPermission(permission);
    }

    public boolean hasWorkspacePermission(long workspaceId, Permission permission) {
        if (workspaces == null) {
            return false;
        }
        Role role = workspaces.get(workspaceId);
        return role != null && role.hasPermission(permission);
    }

    public void merge(UserRoles other) {
        if (other == null) {
            return;
        }

        if (other.global != null) {
            this.global = other.global;
        }

        final Map<Long, Role> otherMap = other.workspaces;
        if (otherMap != null && !otherMap.isEmpty()) {
            Map<Long, Role> map = new HashMap<>(this.workspaces);
            otherMap.forEach((aLong, role) -> {
                if (role != null) {
                    map.put(aLong, role);
                } else {
                    map.remove(aLong);
                }
            });
            this.workspaces = map;
        }
    }
}
