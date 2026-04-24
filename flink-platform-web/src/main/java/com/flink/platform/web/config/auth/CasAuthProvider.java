package com.flink.platform.web.config.auth;

import com.flink.platform.common.exception.DefinitionException;
import com.flink.platform.dao.service.SessionService;
import com.flink.platform.dao.service.UserService;
import com.flink.platform.web.config.AuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import static com.flink.platform.common.enums.ResponseStatus.SSO_AUTH_FAILED;

/**
 * CAS 2.0 authentication provider. Active when {@code auth.type=cas}. Implements the standard
 * Apereo CAS protocol (login redirect + serviceValidate).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "auth.type", havingValue = "cas")
public class CasAuthProvider extends SessionAuthProvider {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public CasAuthProvider(
            UserService userService,
            SessionService sessionService,
            AuthProperties props,
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        super(userService, sessionService, props);
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @NonNull
    public String getLoginRedirectUrl() {
        return UriComponentsBuilder.fromUriString(props.getCas().getServerUrl() + "/login")
                .queryParam("service", props.getFrontendUrl())
                .build(false)
                .toUriString();
    }

    @Override
    @NonNull
    public String getLogoutRedirectUrl() {
        return UriComponentsBuilder.fromUriString(props.getCas().getServerUrl() + "/logout")
                .queryParam("service", props.getFrontendUrl())
                .build(false)
                .toUriString();
    }

    @Override
    public SsoUser handleCallback(String ticket, @Nullable String state) {
        if (StringUtils.isEmpty(ticket)) {
            throw new DefinitionException(SSO_AUTH_FAILED);
        }

        var casProps = props.getCas();
        var validateUrl = UriComponentsBuilder.fromUriString(casProps.getServerUrl() + "/serviceValidate")
                .queryParam("ticket", ticket)
                .queryParam("service", props.getFrontendUrl())
                .queryParam("format", "json")
                .toUriString();

        try {
            var response = restTemplate.getForObject(validateUrl, String.class);
            var root = objectMapper.readTree(response);

            var externalId = root.at(casProps.getExternalIdPath()).asString(null);
            var username = root.at(casProps.getUsernamePath()).asString(null);
            var email = root.at(casProps.getEmailPath()).asString(null);
            return new SsoUser(externalId, username, email);
        } catch (Exception e) {
            log.error("CAS serviceValidate request failed", e);
            throw new DefinitionException(SSO_AUTH_FAILED);
        }
    }
}
