package com.flink.platform.web.config.auth;

import com.flink.platform.common.util.Preconditions;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** User attributes returned by an SSO provider after successful authentication. */
public record SsoUser(
        @NonNull String externalId,
        @NonNull String username,
        @Nullable String email) {

    public SsoUser {
        Preconditions.checkNotNull(externalId, "externalId must not be null");
        Preconditions.checkNotNull(username, "username must not be null");
    }
}
