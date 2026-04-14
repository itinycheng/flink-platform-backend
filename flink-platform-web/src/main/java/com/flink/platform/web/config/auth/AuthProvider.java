package com.flink.platform.web.config.auth;

import com.flink.platform.dao.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;

/**
 * Authentication SPI. Returns the authenticated {@link User}, or {@code null} if credentials are
 * missing or invalid. Implementations are selected via the {@code auth.type} configuration property.
 */
public interface AuthProvider {

    @Nullable
    User authenticate(HttpServletRequest request);
}
