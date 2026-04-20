package com.flink.platform.web.auth;

import com.flink.platform.common.exception.DefinitionException;
import com.flink.platform.dao.service.SessionService;
import com.flink.platform.dao.service.UserService;
import com.flink.platform.web.config.AuthProperties;
import com.flink.platform.web.config.auth.OidcAuthProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OidcAuthProviderTest {

    @Mock
    private UserService userService;

    @Mock
    private SessionService sessionService;

    @Mock
    private RestTemplate restTemplate;

    private AuthProperties props;
    private OidcAuthProvider provider;

    // Minimal OIDC discovery document
    private static final String DISCOVERY_JSON = """
            {"authorization_endpoint":"https://login.example.com/authorize",
             "token_endpoint":"https://login.example.com/token"}
            """;

    @BeforeEach
    void setUp() {
        props = new AuthProperties();
        props.setFrontendUrl("http://localhost:3000/");
        props.getOidc().setAuthority("https://login.example.com");
        props.getOidc().setClientId("client-id");
        props.getOidc().setClientSecret("secret");
        props.getOidc().setScope("openid email profile");

        when(restTemplate.getForObject(
                        eq("https://login.example.com/.well-known/openid-configuration"), eq(String.class)))
                .thenReturn(DISCOVERY_JSON);

        provider = new OidcAuthProvider(
                userService, sessionService, props, restTemplate, new com.fasterxml.jackson.databind.ObjectMapper());
        provider.discoverEndpoints(); // call @PostConstruct manually in tests
    }

    @Test
    void getLoginRedirectUrl_containsRequiredParams() {
        var url = provider.getLoginRedirectUrl();
        assertThat(url).startsWith("https://login.example.com/authorize");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("client_id=client-id");
        assertThat(url).contains("state=");
        assertThat(url).contains("redirect_uri=");
    }

    @Test
    void handleCallback_throwsDefinitionException_whenNoCode() {
        assertThatThrownBy(() -> provider.handleCallback(null, "some-state")).isInstanceOf(DefinitionException.class);
    }

    @Test
    void handleCallback_throwsDefinitionException_whenStateInvalid() {
        assertThatThrownBy(() -> provider.handleCallback("auth-code", "invalid-state-not-in-cache"))
                .isInstanceOf(DefinitionException.class);
    }

    @Test
    void handleCallback_returnsSsoUser_onSuccess() {
        // Prime the state cache by calling getLoginRedirectUrl first
        var redirectUrl = provider.getLoginRedirectUrl();
        var state = redirectUrl.replaceAll(".*[?&]state=([^&]+).*", "$1");

        // Build a minimal ID token: header.payload.signature (signature ignored)
        var payload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("{\"preferred_username\":\"jane.doe\",\"email\":\"jane@example.com\"}".getBytes());
        var idToken = "header." + payload + ".sig";
        var tokenResponseJson = "{\"id_token\":\"" + idToken + "\",\"access_token\":\"at\"}";

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(tokenResponseJson, HttpStatus.OK));

        var ssoUser = provider.handleCallback("auth-code", state);
        assertThat(ssoUser.username()).isEqualTo("jane.doe");
        assertThat(ssoUser.email()).isEqualTo("jane@example.com");
    }

    /**
     * Simulates a realistic Entra ID (Azure AD) id_token payload.
     * Entra ID key claims:
     *   oid  — immutable object ID (stable across UPN renames, recommended unique key)
     *   sub  — subject (stable within a single app registration)
     *   preferred_username — UPN like user@company.com (can change on IT rename)
     *   email — same as UPN in most tenants (optional claim, may be absent)
     *   name  — display name
     *   tid   — tenant ID
     */
    @Test
    void handleCallback_parsesEntraIdToken_correctClaims() {
        var redirectUrl = provider.getLoginRedirectUrl();
        var state = redirectUrl.replaceAll(".*[?&]state=([^&]+).*", "$1");

        // Realistic Entra ID id_token payload
        var entraClaims = """
                {
                  "oid": "87d349ed-44d7-43e1-9a83-5f2406dee5bd",
                  "sub": "AAAAAAAAAAAAAAAAAAAAAIkzqFVrSaSaFHy782bbtaQ",
                  "preferred_username": "tiger@company.com",
                  "email": "tiger@company.com",
                  "name": "Tiger Zhang",
                  "tid": "72f988bf-86f1-41af-91ab-2d7cd011db47"
                }
                """;
        var payload = Base64.getUrlEncoder().withoutPadding().encodeToString(entraClaims.getBytes());
        var idToken = "header." + payload + ".sig";
        var tokenResponseJson = "{\"id_token\":\"" + idToken + "\",\"access_token\":\"at\"}";

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(tokenResponseJson, HttpStatus.OK));

        var ssoUser = provider.handleCallback("auth-code", state);
        assertThat(ssoUser.username()).isEqualTo("tiger@company.com");
        assertThat(ssoUser.email()).isEqualTo("tiger@company.com");
    }

    /**
     * When username-claim=oid (Entra ID recommended config), externalId = oid (immutable) and username =
     * preferred_username (human-readable).
     */
    @Test
    void handleCallback_usesConfiguredClaim_asExternalId() {
        props.getOidc().setUsernameClaim("oid");
        var redirectUrl = provider.getLoginRedirectUrl();
        var state = redirectUrl.replaceAll(".*[?&]state=([^&]+).*", "$1");

        var entraClaims = """
                {"oid":"87d349ed-44d7-43e1-9a83-5f2406dee5bd","preferred_username":"tiger@company.com","email":"tiger@company.com","name":"Tiger Zhang"}
                """;
        var payload = Base64.getUrlEncoder().withoutPadding().encodeToString(entraClaims.getBytes());
        var idToken = "header." + payload + ".sig";
        var tokenResponseJson = "{\"id_token\":\"" + idToken + "\",\"access_token\":\"at\"}";

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(tokenResponseJson, HttpStatus.OK));

        var ssoUser = provider.handleCallback("auth-code", state);
        assertThat(ssoUser.externalId()).isEqualTo("87d349ed-44d7-43e1-9a83-5f2406dee5bd"); // oid → externalId
        assertThat(ssoUser.username()).isEqualTo("tiger@company.com"); // preferred_username → username
        assertThat(ssoUser.email()).isEqualTo("tiger@company.com");
    }

    /** Verifies fallback to email when the configured claim is absent from the token. */
    @Test
    void handleCallback_fallsBackToEmail_whenPreferredUsernameAbsent() {
        var redirectUrl = provider.getLoginRedirectUrl();
        var state = redirectUrl.replaceAll(".*[?&]state=([^&]+).*", "$1");

        var claimsNoUpn = """
                {"oid":"87d349ed-44d7-43e1-9a83-5f2406dee5bd","email":"tiger@company.com","name":"Tiger Zhang"}
                """;
        var payload = Base64.getUrlEncoder().withoutPadding().encodeToString(claimsNoUpn.getBytes());
        var idToken = "header." + payload + ".sig";
        var tokenResponseJson = "{\"id_token\":\"" + idToken + "\",\"access_token\":\"at\"}";

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(tokenResponseJson, HttpStatus.OK));

        var ssoUser = provider.handleCallback("auth-code", state);
        assertThat(ssoUser.username()).isEqualTo("tiger@company.com"); // fell back to email
    }
}
