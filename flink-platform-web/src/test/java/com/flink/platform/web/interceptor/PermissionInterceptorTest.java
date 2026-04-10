package com.flink.platform.web.interceptor;

import com.flink.platform.common.enums.Permission;
import com.flink.platform.common.enums.Role;
import com.flink.platform.common.exception.DefinitionException;
import com.flink.platform.common.model.UserRoles;
import com.flink.platform.dao.entity.User;
import com.flink.platform.web.annotation.RequirePermission;
import com.flink.platform.web.config.interceptor.PermissionInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.method.HandlerMethod;

import java.util.Map;

import static com.flink.platform.common.constants.Constant.SESSION_USER;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionInterceptorTest {

    @InjectMocks
    private PermissionInterceptor interceptor;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HandlerMethod handlerMethod;

    private User userWithRoles(Role globalRole, Map<Long, Role> workspaceRoles) {
        var roles = new UserRoles();
        roles.setGlobal(globalRole);
        roles.setWorkspaces(workspaceRoles);
        var user = new User();
        user.setRoles(roles);
        return user;
    }

    private RequirePermission annotation(Permission permission) {
        var ann = mock(RequirePermission.class);
        doReturn(permission).when(ann).value();
        return ann;
    }

    @Test
    void allowsRequest_whenNoAnnotation() {
        when(handlerMethod.getMethodAnnotation(RequirePermission.class)).thenReturn(null);
        assertTrue(interceptor.preHandle(request, response, handlerMethod));
    }

    @Test
    void allowsRequest_whenHandlerIsNotHandlerMethod() {
        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void allowsSuperAdmin_forGlobalEndpoint() {
        var ann = annotation(Permission.SYSTEM_MANAGE);
        when(handlerMethod.getMethodAnnotation(RequirePermission.class)).thenReturn(ann);
        when(request.getAttribute(SESSION_USER)).thenReturn(userWithRoles(Role.SUPER_ADMIN, Map.of()));
        assertTrue(interceptor.preHandle(request, response, handlerMethod));
    }

    @Test
    void rejects_nonSuperAdmin_forGlobalEndpoint() {
        var ann = annotation(Permission.SYSTEM_MANAGE);
        when(handlerMethod.getMethodAnnotation(RequirePermission.class)).thenReturn(ann);
        when(request.getAttribute(SESSION_USER)).thenReturn(userWithRoles(null, Map.of(1L, Role.ADMIN)));
        assertThrows(DefinitionException.class, () -> interceptor.preHandle(request, response, handlerMethod));
    }

    @Test
    void allowsDeveloper_forTaskEdit_inCorrectWorkspace() {
        var ann = annotation(Permission.TASK_EDIT);
        when(handlerMethod.getMethodAnnotation(RequirePermission.class)).thenReturn(ann);
        when(request.getAttribute(SESSION_USER)).thenReturn(userWithRoles(null, Map.of(1L, Role.DEVELOPER)));
        when(request.getHeader("X-Workspace-Id")).thenReturn("1");
        assertTrue(interceptor.preHandle(request, response, handlerMethod));
    }

    @Test
    void rejectsOperator_forTaskEdit() {
        var ann = annotation(Permission.TASK_EDIT);
        when(handlerMethod.getMethodAnnotation(RequirePermission.class)).thenReturn(ann);
        when(request.getAttribute(SESSION_USER)).thenReturn(userWithRoles(null, Map.of(1L, Role.OPERATOR)));
        when(request.getHeader("X-Workspace-Id")).thenReturn("1");
        assertThrows(DefinitionException.class, () -> interceptor.preHandle(request, response, handlerMethod));
    }

    @Test
    void rejects_whenMissingWorkspaceIdHeader() {
        var ann = annotation(Permission.TASK_VIEW);
        when(handlerMethod.getMethodAnnotation(RequirePermission.class)).thenReturn(ann);
        when(request.getAttribute(SESSION_USER)).thenReturn(userWithRoles(null, Map.of(1L, Role.DEVELOPER)));
        when(request.getHeader("X-Workspace-Id")).thenReturn(null);
        assertThrows(DefinitionException.class, () -> interceptor.preHandle(request, response, handlerMethod));
    }

    @Test
    void rejects_whenWorkspaceIdIsInvalid() {
        var ann = annotation(Permission.TASK_VIEW);
        when(handlerMethod.getMethodAnnotation(RequirePermission.class)).thenReturn(ann);
        when(request.getAttribute(SESSION_USER)).thenReturn(userWithRoles(null, Map.of(1L, Role.DEVELOPER)));
        when(request.getHeader("X-Workspace-Id")).thenReturn("not-a-number");
        assertThrows(DefinitionException.class, () -> interceptor.preHandle(request, response, handlerMethod));
    }

    @Test
    void rejects_userWithNullRoles() {
        var ann = annotation(Permission.TASK_VIEW);
        when(handlerMethod.getMethodAnnotation(RequirePermission.class)).thenReturn(ann);
        var user = new User();
        user.setRoles(null);
        when(request.getAttribute(SESSION_USER)).thenReturn(user);
        when(request.getHeader("X-Workspace-Id")).thenReturn("1");
        assertThrows(DefinitionException.class, () -> interceptor.preHandle(request, response, handlerMethod));
    }

    @Test
    void rejects_whenUserAttributeIsNull() {
        when(handlerMethod.getMethodAnnotation(RequirePermission.class)).thenReturn(mock(RequirePermission.class));
        when(request.getAttribute(SESSION_USER)).thenReturn(null);
        assertThrows(DefinitionException.class, () -> interceptor.preHandle(request, response, handlerMethod));
    }

    @Test
    void rejects_userWithNoRoleInRequestedWorkspace() {
        var ann = annotation(Permission.TASK_VIEW);
        when(handlerMethod.getMethodAnnotation(RequirePermission.class)).thenReturn(ann);
        when(request.getAttribute(SESSION_USER)).thenReturn(userWithRoles(null, Map.of(99L, Role.DEVELOPER)));
        when(request.getHeader("X-Workspace-Id")).thenReturn("1");
        assertThrows(DefinitionException.class, () -> interceptor.preHandle(request, response, handlerMethod));
    }
}
