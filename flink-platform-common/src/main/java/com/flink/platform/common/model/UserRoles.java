package com.flink.platform.common.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
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
    @JsonSetter(nulls = Nulls.SKIP)
    private Map<Long, Role> workspaces = Collections.emptyMap();

    public boolean hasGlobalPermission(Permission permission) {
        return global != null && global.hasPermission(permission);
    }

    public boolean hasWorkspacePermission(long workspaceId, Permission permission) {
        Role role = workspaces.get(workspaceId);
        return role != null && role.hasPermission(permission);
    }

    public boolean hasWorkspaceRole(Long workspaceId) {
        return workspaces.get(workspaceId) != null;
    }

    @JsonIgnore
    public Long getAnyWorkspaceId() {
        return workspaces.isEmpty() ? null : workspaces.keySet().iterator().next();
    }

    public void merge(UserRoles other) {
        if (other.global != null) {
            this.global = other.global;
        }

        if (!other.workspaces.isEmpty()) {
            Map<Long, Role> map = new HashMap<>(this.workspaces);
            other.workspaces.forEach((id, role) -> {
                if (role != null) {
                    map.put(id, role);
                } else {
                    map.remove(id);
                }
            });
            this.workspaces = map;
        }
    }
}
