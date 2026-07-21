package com.flink.platform.web.config;

import com.fasterxml.jackson.annotation.JsonValue;

import static java.util.Locale.ROOT;

/** Supported authentication modes, configured via {@code auth.type}. */
public enum AuthType {
    LOCAL,
    CAS,
    OIDC;

    @JsonValue
    public String value() {
        return name().toLowerCase(ROOT);
    }
}
