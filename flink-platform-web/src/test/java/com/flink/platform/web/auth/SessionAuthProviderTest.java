package com.flink.platform.web.auth;

import com.flink.platform.dao.entity.Session;
import com.flink.platform.dao.service.SessionService;
import com.flink.platform.dao.service.UserService;
import com.flink.platform.web.config.AuthProperties;
import com.flink.platform.web.config.auth.SessionAuthProvider;
import com.flink.platform.web.config.auth.SsoUser;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionAuthProviderTest {

    @Mock
    private UserService userService;

    @Mock
    private SessionService sessionService;

    @Mock
    private HttpServletRequest request;

    private AuthProperties props;
    private TestableProvider provider;

    // Concrete subclass for testing the abstract class
    static class TestableProvider extends SessionAuthProvider {
        TestableProvider(UserService u, SessionService s, AuthProperties p) {
            super(u, s, p);
        }

        @Override
        public String getLoginRedirectUrl() {
            return "";
        }

        @Override
        public SsoUser handleCallback(String ticketOrCode, @Nullable String state) {
            return null;
        }
    }

    @BeforeEach
    void setUp() {
        props = new AuthProperties();
        provider = new TestableProvider(userService, sessionService, props);
    }

    @Test
    void authenticate_returnsNull_whenNoXToken() {
        when(request.getHeader("X-Token")).thenReturn(null);
        assertThat(provider.authenticate(request)).isNull();
    }

    @Test
    void authenticate_returnsNull_whenSessionNotFound() {
        when(request.getHeader("X-Token")).thenReturn("tok123");
        when(sessionService.getOne(any())).thenReturn(null);
        assertThat(provider.authenticate(request)).isNull();
    }

    @Test
    void authenticate_deletesSession_whenExpired() {
        props.setSessionTtl("7d");
        var session = sessionWithAge(8); // 8 days old → expired
        when(request.getHeader("X-Token")).thenReturn("tok");
        when(sessionService.getOne(any())).thenReturn(session);

        assertThat(provider.authenticate(request)).isNull();
        verify(sessionService).removeById(session.getId());
    }

    private Session sessionWithAge(int daysOld) {
        var s = new Session();
        s.setId(1L);
        s.setToken("tok");
        s.setUserId(42L);
        s.setLastLoginTime(LocalDateTime.now().minusDays(daysOld));
        return s;
    }
}
