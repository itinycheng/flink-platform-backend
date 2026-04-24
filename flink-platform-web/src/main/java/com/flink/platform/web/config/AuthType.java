package com.flink.platform.web.config;

import com.fasterxml.jackson.annotation.JsonValue;

/** Supported authentication modes, configured via {@code auth.type}. */
public enum AuthType {
    LOCAL,
    CAS,
    OIDC;

    @JsonValue
    public String value() {
        return name().toLowerCase();
    }
}
