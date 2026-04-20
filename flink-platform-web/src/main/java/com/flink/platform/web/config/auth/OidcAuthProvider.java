package com.flink.platform.web.config.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flink.platform.common.exception.DefinitionException;
import com.flink.platform.dao.service.SessionService;
import com.flink.platform.dao.service.UserService;
import com.flink.platform.web.config.AuthProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.flink.platform.common.enums.ResponseStatus.SSO_AUTH_FAILED;

/**
 * OIDC Authorization Code flow provider. Active when {@code auth.type=oidc}.
 * Works with any OIDC-compliant provider (Entra ID, Casdoor OIDC, Okta, etc.)
 * via standard discovery at {@code {authority}/.well-known/openid-configuration}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "auth.type", havingValue = "oidc")
public class OidcAuthProvider extends SessionAuthProvider {

    private static final int STATE_TTL_SECONDS = 300;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /** Discovered endpoints, cached at startup. */
    private String authorizationEndpoint;

    private String tokenEndpoint;

    /** Short-lived state store for CSRF protection: state → expiry. */
    private final ConcurrentHashMap<String, Instant> stateCache = new ConcurrentHashMap<>();

    @Autowired
    public OidcAuthProvider(
            UserService userService,
            SessionService sessionService,
            AuthProperties props,
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        super(userService, sessionService, props);
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void discoverEndpoints() {
        var discoveryUrl = props.getOidc().getAuthority() + "/.well-known/openid-configuration";
        try {
            String json = restTemplate.getForObject(discoveryUrl, String.class);
            if (json == null) {
                throw new IllegalStateException("Empty response from OIDC discovery endpoint");
            }
            JsonNode node = objectMapper.readTree(json);
            authorizationEndpoint = node.path("authorization_endpoint").asText();
            tokenEndpoint = node.path("token_endpoint").asText();
            log.info("OIDC discovery complete. auth={} token={}", authorizationEndpoint, tokenEndpoint);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("OIDC discovery failed for " + discoveryUrl, e);
        }
    }

    @Override
    public String getLoginRedirectUrl() {
        var state = UUID.randomUUID().toString();
        var expiry = Instant.now().plusSeconds(STATE_TTL_SECONDS);
        // Evict expired entries to prevent unbounded growth
        stateCache.entrySet().removeIf(e -> Instant.now().isAfter(e.getValue()));
        stateCache.put(state, expiry);

        return UriComponentsBuilder.fromUriString(authorizationEndpoint)
                .queryParam("response_type", "code")
                .queryParam("client_id", props.getOidc().getClientId())
                .queryParam("redirect_uri", props.getFrontendUrl())
                .queryParam("scope", props.getOidc().getScope())
                .queryParam("state", state)
                .build(false)
                .toUriString();
    }

    @Override
    public SsoUser handleCallback(String code, @Nullable String state) {
        if (StringUtils.isEmpty(code)) {
            log.warn("OIDC callback missing 'code' parameter");
            throw new DefinitionException(SSO_AUTH_FAILED);
        }

        if (StringUtils.isEmpty(state)) {
            log.warn("OIDC callback missing state parameter");
            throw new DefinitionException(SSO_AUTH_FAILED);
        }
        var stateExpiry = stateCache.remove(state); // atomic: claim the state
        if (stateExpiry == null || Instant.now().isAfter(stateExpiry)) {
            log.warn("OIDC callback invalid or expired state: {}", state);
            throw new DefinitionException(SSO_AUTH_FAILED);
        }

        var tokenResponse = exchangeCode(code, props.getFrontendUrl());
        return extractUser(tokenResponse);
    }

    private String exchangeCode(String code, String callbackUrl) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        var body = new LinkedMultiValueMap<String, String>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", callbackUrl);
        body.add("client_id", props.getOidc().getClientId());
        body.add("client_secret", props.getOidc().getClientSecret());

        try {
            var response = restTemplate.postForEntity(tokenEndpoint, new HttpEntity<>(body, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("OIDC token exchange failed: {}", response.getStatusCode());
                throw new DefinitionException(SSO_AUTH_FAILED);
            }
            return response.getBody();
        } catch (DefinitionException e) {
            throw e;
        } catch (Exception e) {
            log.error("OIDC token exchange request failed", e);
            throw new DefinitionException(SSO_AUTH_FAILED);
        }
    }

    private SsoUser extractUser(String tokenResponseJson) {
        try {
            JsonNode root = objectMapper.readTree(tokenResponseJson);
            // Prefer id_token for user info (access_token may not contain email — per Entra ID docs)
            String idToken = root.path("id_token").asText(null);
            if (StringUtils.isEmpty(idToken)) {
                log.error("OIDC token response missing id_token");
                throw new DefinitionException(SSO_AUTH_FAILED);
            }

            // Decode JWT payload (middle part) — no signature verification needed;
            // token comes directly from the trusted token endpoint, not from the user.
            String[] parts = idToken.split("\\.");
            if (parts.length < 3) {
                log.error("OIDC id_token is not a valid JWT (expected 3 parts, got {})", parts.length);
                throw new DefinitionException(SSO_AUTH_FAILED);
            }
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> claims = objectMapper.readValue(payloadBytes, new TypeReference<>() {});

            // externalId: the stable, immutable identifier configured via username-claim (e.g. oid, sub)
            String externalId = (String) claims.getOrDefault(props.getOidc().getUsernameClaim(), claims.get("email"));
            if (StringUtils.isEmpty(externalId)) {
                log.error(
                        "OIDC id_token missing configured claim '{}' and email",
                        props.getOidc().getUsernameClaim());
                throw new DefinitionException(SSO_AUTH_FAILED);
            }

            String email = (String) claims.get("email");
            // username: human-readable display name; prefer preferred_username, fall back to email or externalId
            String username = (String) claims.get("preferred_username");
            if (StringUtils.isEmpty(username)) {
                username = StringUtils.isNotEmpty(email) ? email : externalId;
            }
            return new SsoUser(externalId, username, email);
        } catch (DefinitionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse OIDC id_token", e);
            throw new DefinitionException(SSO_AUTH_FAILED);
        }
    }
}
