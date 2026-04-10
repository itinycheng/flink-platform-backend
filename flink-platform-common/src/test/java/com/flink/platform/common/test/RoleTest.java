package com.flink.platform.common.test;

import com.flink.platform.common.enums.Permission;
import com.flink.platform.common.enums.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleTest {

    @Test
    void superAdmin_hasSystemPermissions() {
        assertTrue(Role.SUPER_ADMIN.hasPermission(Permission.SYSTEM_MANAGE));
        assertTrue(Role.SUPER_ADMIN.hasPermission(Permission.WORKSPACE_MANAGE));
        assertTrue(Role.SUPER_ADMIN.hasPermission(Permission.WORKSPACE_VIEW));
    }

    @Test
    void superAdmin_doesNotHaveWorkspaceBusinessPermissions() {
        assertFalse(Role.SUPER_ADMIN.hasPermission(Permission.TASK_EDIT));
        assertFalse(Role.SUPER_ADMIN.hasPermission(Permission.TASK_EXEC));
        assertFalse(Role.SUPER_ADMIN.hasPermission(Permission.TASK_VIEW));
    }

    @Test
    void admin_hasAllWorkspacePermissions() {
        assertTrue(Role.ADMIN.hasPermission(Permission.WORKSPACE_MANAGE));
        assertTrue(Role.ADMIN.hasPermission(Permission.WORKSPACE_VIEW));
        assertTrue(Role.ADMIN.hasPermission(Permission.TASK_EDIT));
        assertTrue(Role.ADMIN.hasPermission(Permission.TASK_EXEC));
        assertTrue(Role.ADMIN.hasPermission(Permission.TASK_VIEW));
    }

    @Test
    void admin_doesNotHaveSystemPermissions() {
        assertFalse(Role.ADMIN.hasPermission(Permission.SYSTEM_MANAGE));
    }

    @Test
    void lowerRoles_doNotHaveSystemOrManagePermissions() {
        for (Role role : new Role[] {Role.DEVELOPER, Role.OPERATOR, Role.VIEWER}) {
            assertFalse(role.hasPermission(Permission.SYSTEM_MANAGE), role + " should not have SYSTEM_MANAGE");
            assertFalse(role.hasPermission(Permission.WORKSPACE_MANAGE), role + " should not have WORKSPACE_MANAGE");
        }
    }

    @Test
    void developer_canEditAndExecute_butNotManage() {
        assertTrue(Role.DEVELOPER.hasPermission(Permission.TASK_EDIT));
        assertTrue(Role.DEVELOPER.hasPermission(Permission.TASK_EXEC));
        assertFalse(Role.DEVELOPER.hasPermission(Permission.WORKSPACE_MANAGE));
        assertFalse(Role.DEVELOPER.hasPermission(Permission.SYSTEM_MANAGE));
    }

    @Test
    void operator_canExecute_butNotEdit() {
        assertTrue(Role.OPERATOR.hasPermission(Permission.TASK_EXEC));
        assertTrue(Role.OPERATOR.hasPermission(Permission.TASK_VIEW));
        assertFalse(Role.OPERATOR.hasPermission(Permission.TASK_EDIT));
    }

    @Test
    void viewer_canOnlyView() {
        assertTrue(Role.VIEWER.hasPermission(Permission.TASK_VIEW));
        assertTrue(Role.VIEWER.hasPermission(Permission.WORKSPACE_VIEW));
        assertFalse(Role.VIEWER.hasPermission(Permission.TASK_EDIT));
        assertFalse(Role.VIEWER.hasPermission(Permission.TASK_EXEC));
    }
}
