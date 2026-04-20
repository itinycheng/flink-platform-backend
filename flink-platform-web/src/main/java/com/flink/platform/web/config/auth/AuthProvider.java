package com.flink.platform.web.config.auth;

import com.flink.platform.dao.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;

/**
 * Authentication SPI. All auth modes (token, CAS, OIDC) implement this interface.
 * Implementations are selected via the {@code auth.type} configuration property.
 */
public interface AuthProvider {

    /** SSO redirect URL for the login button; {@code null} in token mode. */
    default String getLoginRedirectUrl() {
        return null;
    }

    /**
     * Validates an SSO callback and returns the authenticated SSO user.
     */
    default SsoUser handleCallback(String ticketOrCode, @Nullable String state) {
        throw new UnsupportedOperationException("Not an SSO provider");
    }

    /** Authenticates a request via the {@code X-Token} header. Returns {@code null} if invalid. */
    @Nullable
    User authenticate(HttpServletRequest request);
}
