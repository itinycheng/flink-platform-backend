package com.flink.platform.web.entity.request;

import lombok.Data;

/** Unified login request. */
@Data
public class LoginRequest {

    private String username;
    private String password;
    /** CAS service ticket obtained from the redirect URL query parameter. */
    private String ticket;
    /** OIDC authorization code obtained from the redirect URL query parameter. */
    private String code;
    /**
     * OIDC state value for CSRF validation (must match the value issued by
     * {@code GET /login/config}).
     */
    private String state;

    private Long workspaceId;
}
