package com.flink.platform.web.config.interceptor;

import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.context.UserContext;
import com.flink.platform.web.config.auth.AuthHandler;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Login interceptor: authenticates the request by delegating to {@link AuthHandler}. */
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class LoginInterceptor implements HandlerInterceptor {

    private final AuthHandler authHandler;

    @Override
    public boolean preHandle(
            @Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        var user = authHandler.authenticate(request);
        request.setAttribute(Constant.SESSION_USER, user);
        UserContext.set(user.getId());
        return true;
    }

    @Override
    public void afterCompletion(
            @Nonnull HttpServletRequest request,
            @Nonnull HttpServletResponse response,
            @Nonnull Object handler,
            Exception ex) {
        UserContext.clear();
    }
}
