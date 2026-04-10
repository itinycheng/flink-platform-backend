package com.flink.platform.common.model;

import com.flink.platform.common.enums.Permission;
import com.flink.platform.common.enums.Role;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Map;

/** User roles stored as JSON in t_user.roles. */
@Data
@NoArgsConstructor
public class UserRoles {

    /** System-level role. Null means no system permissions. Only SUPER_ADMIN is valid here. */
    private Role global;

    /**
     * Workspace-level roles. Key is workspace ID, value is the role within that workspace.
     * Only ADMIN/DEVELOPER/OPERATOR/VIEWER are valid values.
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
}
