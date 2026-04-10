package com.flink.platform.web.config.interceptor;

import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.exception.DefinitionException;
import com.flink.platform.dao.entity.User;
import com.flink.platform.web.annotation.RequirePermission;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import static com.flink.platform.common.enums.ResponseStatus.INVALID_WORKSPACE_ID;
import static com.flink.platform.common.enums.ResponseStatus.USER_HAVE_NO_PERMISSION;

/** Permission interceptor: checks @RequirePermission on controller methods. */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            @Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        var annotation = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (annotation == null) {
            return true;
        }

        var user = (User) request.getAttribute(Constant.SESSION_USER);
        if (user == null) {
            throw new DefinitionException(USER_HAVE_NO_PERMISSION);
        }
        var permission = annotation.value();
        var userRoles = user.getRoles();

        // Global role check first (e.g. SUPER_ADMIN with SYSTEM_MANAGE)
        if (userRoles != null && userRoles.hasGlobalPermission(permission)) {
            return true;
        }

        // Workspace-level permission check
        var workspaceIdStr = request.getHeader("X-Workspace-Id");
        if (StringUtils.isEmpty(workspaceIdStr)) {
            throw new DefinitionException(INVALID_WORKSPACE_ID);
        }

        long workspaceId;
        try {
            workspaceId = Long.parseLong(workspaceIdStr);
        } catch (NumberFormatException e) {
            throw new DefinitionException(INVALID_WORKSPACE_ID);
        }

        if (userRoles == null || !userRoles.hasWorkspacePermission(workspaceId, permission)) {
            throw new DefinitionException(USER_HAVE_NO_PERMISSION);
        }

        return true;
    }
}
