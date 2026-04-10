package com.flink.platform.common.test;

import com.flink.platform.common.enums.Permission;
import com.flink.platform.common.enums.Role;
import com.flink.platform.common.model.UserRoles;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserRolesTest {

    @Test
    void hasGlobalPermission_whenSuperAdmin() {
        UserRoles roles = new UserRoles();
        roles.setGlobal(Role.SUPER_ADMIN);
        assertTrue(roles.hasGlobalPermission(Permission.SYSTEM_MANAGE));
        assertTrue(roles.hasGlobalPermission(Permission.WORKSPACE_MANAGE));
    }

    @Test
    void noGlobalPermission_whenGlobalRoleIsNull() {
        UserRoles roles = new UserRoles();
        assertFalse(roles.hasGlobalPermission(Permission.SYSTEM_MANAGE));
    }

    @Test
    void hasWorkspacePermission_forCorrectWorkspace() {
        UserRoles roles = new UserRoles();
        roles.setWorkspaces(Collections.singletonMap(1L, Role.DEVELOPER));
        assertTrue(roles.hasWorkspacePermission(1L, Permission.TASK_EDIT));
        assertTrue(roles.hasWorkspacePermission(1L, Permission.TASK_VIEW));
    }

    @Test
    void noWorkspacePermission_forDifferentWorkspace() {
        UserRoles roles = new UserRoles();
        roles.setWorkspaces(Collections.singletonMap(1L, Role.DEVELOPER));
        assertFalse(roles.hasWorkspacePermission(2L, Permission.TASK_EDIT));
    }

    @Test
    void operator_canExecute_butNotEdit_inWorkspace() {
        UserRoles roles = new UserRoles();
        roles.setWorkspaces(Collections.singletonMap(1L, Role.OPERATOR));
        assertTrue(roles.hasWorkspacePermission(1L, Permission.TASK_EXEC));
        assertFalse(roles.hasWorkspacePermission(1L, Permission.TASK_EDIT));
    }

    @Test
    void nullWorkspaceRoles_returnsNoPermission() {
        UserRoles roles = new UserRoles();
        roles.setWorkspaces(null);
        assertFalse(roles.hasWorkspacePermission(1L, Permission.TASK_VIEW));
    }

    @Test
    void defaultInstance_hasNoPermissions() {
        UserRoles roles = new UserRoles();
        assertFalse(roles.hasWorkspacePermission(1L, Permission.TASK_VIEW));
        assertFalse(roles.hasGlobalPermission(Permission.SYSTEM_MANAGE));
    }

    @Test
    void viewer_canOnlyView_inWorkspace() {
        UserRoles roles = new UserRoles();
        roles.setWorkspaces(Collections.singletonMap(1L, Role.VIEWER));
        assertTrue(roles.hasWorkspacePermission(1L, Permission.TASK_VIEW));
        assertTrue(roles.hasWorkspacePermission(1L, Permission.WORKSPACE_VIEW));
        assertFalse(roles.hasWorkspacePermission(1L, Permission.TASK_EDIT));
        assertFalse(roles.hasWorkspacePermission(1L, Permission.TASK_EXEC));
    }

    @Test
    void admin_hasFullAccess_inWorkspace() {
        UserRoles roles = new UserRoles();
        roles.setWorkspaces(Collections.singletonMap(1L, Role.ADMIN));
        assertTrue(roles.hasWorkspacePermission(1L, Permission.TASK_EDIT));
        assertTrue(roles.hasWorkspacePermission(1L, Permission.TASK_EXEC));
        assertTrue(roles.hasWorkspacePermission(1L, Permission.WORKSPACE_MANAGE));
    }
}
