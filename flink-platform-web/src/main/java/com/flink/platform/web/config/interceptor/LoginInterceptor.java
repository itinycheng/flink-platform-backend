package com.flink.platform.web.config.interceptor;

import com.flink.platform.common.constants.Constant;
import com.flink.platform.dao.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.time.LocalDateTime;

/** Login interceptor. */
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

    /** TODO Intercept the execution of a handler. Called after HandlerMapping determined. */
    @Override
    public boolean preHandle(
            HttpServletRequest request,
            @Nonnull HttpServletResponse response,
            @Nonnull Object handler) {
        User user = new User();
        user.setId(1);
        user.setUserName("test");
        user.setPassword("password");
        user.setCreateTime(LocalDateTime.now());
        request.setAttribute(Constant.SESSION_USER, user);
        return true;
    }
}
