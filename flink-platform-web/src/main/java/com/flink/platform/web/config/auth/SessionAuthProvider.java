package com.flink.platform.web.config.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.dao.entity.Session;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.SessionService;
import com.flink.platform.dao.service.UserService;
import com.flink.platform.web.config.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;

/**
 * Base for SSO auth providers. Handles X-Token → t_user_session validation (with optional TTL) and
 * session creation. Subclasses implement the SSO-specific login flow.
 */
@Slf4j
public abstract class SessionAuthProvider implements AuthProvider {

    protected final UserService userService;

    protected final SessionService sessionService;

    protected final AuthProperties props;

    protected SessionAuthProvider(UserService userService, SessionService sessionService, AuthProperties props) {
        this.userService = userService;
        this.sessionService = sessionService;
        this.props = props;
    }

    @Override
    public @Nullable User authenticate(HttpServletRequest request) {
        var token = request.getHeader("X-Token");
        if (StringUtils.isEmpty(token)) {
            log.info("token not found in request header.");
            return null;
        }

        var session = sessionService.getOne(new QueryWrapper<Session>().lambda().eq(Session::getToken, token));
        if (session == null) {
            log.info("Session not found for token.");
            return null;
        }

        var now = LocalDateTime.now();
        var expiry = session.getLastLoginTime().plus(props.getSessionTtl());
        if (now.isAfter(expiry)) {
            log.info("Session expired for userId: {}", session.getUserId());
            sessionService.removeById(session.getId());
            return null;
        }

        var user = userService.getById(session.getUserId());
        if (user == null) {
            log.info("User not found for session userId: {}", session.getUserId());
        }
        return user;
    }
}
