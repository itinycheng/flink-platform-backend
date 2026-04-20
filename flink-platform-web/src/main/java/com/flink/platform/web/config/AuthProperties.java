package com.flink.platform.web.config;

import com.flink.platform.common.util.DurationUtil;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

import static com.flink.platform.web.config.AuthType.CAS;

/** Externalized configuration for all auth modes. */
@Data
@Validated
@Configuration
@ConfigurationProperties("auth")
public class AuthProperties {

    @NotNull
    private AuthType type;

    private String frontendUrl;

    @Setter(AccessLevel.NONE)
    private Duration sessionTtl;

    private Cas cas = new Cas();

    private Oidc oidc = new Oidc();

    @Data
    public static class Cas {
        private String serverUrl;
        private String externalIdPath;
        private String usernamePath;
        private String emailPath;
    }

    @Data
    public static class Oidc {

        private String authority;

        private String clientId;

        private String clientSecret;

        private String scope = "openid email profile";

        private String usernameClaim = "preferred_username";
    }

    @SuppressWarnings("unused")
    public void setSessionTtl(String sessionTtl) {
        this.sessionTtl = DurationUtil.parse(sessionTtl);
    }

    @SuppressWarnings("unused")
    @AssertTrue(message = "cas.server-url must be configured when auth.type=cas")
    public boolean isCasServerUrlConfigured() {
        if (!CAS.equals(type)) {
            return true;
        }

        return StringUtils.isNotBlank(cas.getServerUrl());
    }

    @SuppressWarnings("unused")
    @AssertTrue(message = "oidc.authority/client-id/client-secret must be configured when auth.type=oidc")
    public boolean isOidcConfigured() {
        if (type != AuthType.OIDC) {
            return true;
        }

        return StringUtils.isNotBlank(oidc.getAuthority())
                && StringUtils.isNotBlank(oidc.getClientId())
                && StringUtils.isNotBlank(oidc.getClientSecret());
    }
}
