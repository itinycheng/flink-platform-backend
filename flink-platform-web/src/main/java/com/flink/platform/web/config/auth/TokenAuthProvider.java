package com.flink.platform.web.config.auth;

import com.flink.platform.dao.service.SessionService;
import com.flink.platform.dao.service.UserService;
import com.flink.platform.web.config.AuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Token-based authenticator. Active when {@code auth.type=token} (default). */
@Slf4j
@Component
@ConditionalOnProperty(name = "auth.type", havingValue = "token", matchIfMissing = true)
public class TokenAuthProvider extends SessionAuthProvider {

    @Autowired
    public TokenAuthProvider(UserService userService, SessionService sessionService, AuthProperties props) {
        super(userService, sessionService, props);
    }

    @Override
    public @Nullable String getLoginRedirectUrl() {
        return null;
    }
}
