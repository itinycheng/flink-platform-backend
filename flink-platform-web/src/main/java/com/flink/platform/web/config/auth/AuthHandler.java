package com.flink.platform.web.config.auth;

import com.flink.platform.dao.entity.User;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Authentication handler SPI.
 * Implementations are selected via the {@code auth.type} configuration property.
 * Throw {@link com.flink.platform.common.exception.DefinitionException} on authentication failure.
 */
public interface AuthHandler {

    User authenticate(HttpServletRequest request);
}
