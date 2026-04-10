package com.flink.platform.web.config.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.exception.DefinitionException;
import com.flink.platform.dao.entity.Session;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.SessionService;
import com.flink.platform.dao.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static com.flink.platform.common.enums.ResponseStatus.UNAUTHORIZED;
import static com.flink.platform.common.enums.ResponseStatus.USER_LOCKED;
import static com.flink.platform.common.enums.UserStatus.LOCKED;

/** Token-based authentication handler. Active when {@code auth.type=token} (default). */
@Slf4j
@Component
@ConditionalOnProperty(name = "auth.type", havingValue = "token", matchIfMissing = true)
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class TokenAuthHandler implements AuthHandler {

    private final UserService userService;

    private final SessionService sessionService;

    @Override
    public User authenticate(HttpServletRequest request) {
        var token = request.getHeader("X-Token");
        if (StringUtils.isEmpty(token)) {
            throw new DefinitionException(UNAUTHORIZED);
        }

        var session = sessionService.getOne(new QueryWrapper<Session>().lambda().eq(Session::getToken, token));
        if (session == null) {
            log.info("Session not found for token.");
            throw new DefinitionException(UNAUTHORIZED);
        }

        var user = userService.getById(session.getUserId());
        if (user == null) {
            log.info("User not found for session userId: {}", session.getUserId());
            throw new DefinitionException(UNAUTHORIZED);
        }

        if (LOCKED == user.getStatus()) {
            log.info("User is locked: {}", user.getUsername());
            throw new DefinitionException(USER_LOCKED);
        }

        return user;
    }
}
