package com.flink.platform.web.config.interceptor;

import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.exception.DefinitionException;
import com.flink.platform.common.util.NumberUtil;
import com.flink.platform.web.annotation.WorkspaceOptional;
import com.flink.platform.web.common.RequestContext;
import com.flink.platform.web.config.auth.AuthProvider;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import static com.flink.platform.common.enums.ResponseStatus.INVALID_WORKSPACE_ID;
import static com.flink.platform.common.enums.ResponseStatus.UNAUTHORIZED;
import static com.flink.platform.common.enums.ResponseStatus.USER_LOCKED;
import static com.flink.platform.common.enums.UserStatus.LOCKED;

/** Login interceptor: authenticates the request and checks account status. */
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class LoginInterceptor implements HandlerInterceptor {

    private final AuthProvider authProvider;

    @Override
    public boolean preHandle(
            @Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        var user = authProvider.authenticate(request);
        if (user == null) {
            throw new DefinitionException(UNAUTHORIZED);
        }

        if (LOCKED.equals(user.getStatus())) {
            log.info("User is locked: {}", user.getUsername());
            throw new DefinitionException(USER_LOCKED);
        }

        var workspaceId = NumberUtil.toLong(request.getHeader("X-Workspace-Id"));
        if (workspaceId == null && !isWorkspaceOptional(handler)) {
            throw new DefinitionException(INVALID_WORKSPACE_ID);
        }

        request.setAttribute(Constant.SESSION_USER, user);
        RequestContext.set(new RequestContext.Context(user.getId(), workspaceId));
        return true;
    }

    private boolean isWorkspaceOptional(Object handler) {
        if (handler instanceof HandlerMethod m) {
            return m.hasMethodAnnotation(WorkspaceOptional.class);
        }
        return false;
    }

    @Override
    public void afterCompletion(
            @Nonnull HttpServletRequest request,
            @Nonnull HttpServletResponse response,
            @Nonnull Object handler,
            Exception ex) {
        RequestContext.clear();
    }
}
