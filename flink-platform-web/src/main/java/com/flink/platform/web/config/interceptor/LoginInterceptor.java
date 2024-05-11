package com.flink.platform.web.config.interceptor;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.SessionService;
import com.flink.platform.dao.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Login interceptor. */
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

    @Autowired private UserService userService;

    @Autowired private SessionService sessionService;

    @Override
    public boolean preHandle(
            @Nonnull HttpServletRequest request,
            @Nonnull HttpServletResponse response,
            @Nonnull Object handler) {

        // get token
        String token = request.getHeader("X-Token");
        if (StringUtils.isEmpty(token)) {
            return false;
        }

        User user =
                userService.getOne(
                        new QueryWrapper<User>()
                                .lambda()
                                .like(User::getEmail, token.trim())
                                .orderByDesc(User::getId)
                                .last("LIMIT 1"));
        if (user == null) {
            return false;
        }

        if ("LOCK".equals(user.getStatus())) {
            response.setStatus(HttpStatus.SC_UNAUTHORIZED);
            log.info("User: {} locked.", user);
            return false;
        }

        request.setAttribute(Constant.SESSION_USER, user);
        return true;
    }
}
