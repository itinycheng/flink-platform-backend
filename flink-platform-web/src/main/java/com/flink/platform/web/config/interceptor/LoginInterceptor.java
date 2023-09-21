package com.flink.platform.web.config.interceptor;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.dao.entity.Session;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.SessionService;
import com.flink.platform.dao.service.UserService;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.HandlerInterceptor;

/** Login interceptor. */
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

    @Autowired
    private UserService userService;

    @Autowired
    private SessionService sessionService;

    @Override
    public boolean preHandle(
            @Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull Object handler) {

        // get token
        String token = request.getHeader("X-Token");
        if (StringUtils.isEmpty(token)) {
            return false;
        }

        Session session =
                sessionService.getOne(new QueryWrapper<Session>().lambda().eq(Session::getToken, token));
        if (session == null) {
            response.setStatus(HttpStatus.SC_UNAUTHORIZED);
            log.info("session: {} does not exist.", token);
            return false;
        }

        long userId = session.getUserId();
        User user = userService.getById(userId);
        if ("LOCK".equals(user.getStatus())) {
            response.setStatus(HttpStatus.SC_UNAUTHORIZED);
            log.info("User: {} locked.", user);
            return false;
        }

        request.setAttribute(Constant.SESSION_USER, user);
        return true;
    }
}
