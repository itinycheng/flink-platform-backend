package com.flink.platform.web.auth;

import com.flink.platform.common.exception.DefinitionException;
import com.flink.platform.dao.service.SessionService;
import com.flink.platform.dao.service.UserService;
import com.flink.platform.web.config.AuthProperties;
import com.flink.platform.web.config.auth.CasAuthProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CasAuthProviderTest {

    @Mock
    private UserService userService;

    @Mock
    private SessionService sessionService;

    @Mock
    private RestTemplate restTemplate;

    private AuthProperties props;
    private CasAuthProvider provider;

    @BeforeEach
    void setUp() {
        props = new AuthProperties();
        props.setFrontendUrl("http://localhost:3000/");
        props.getCas().setServerUrl("https://cas.example.com/cas/tiger/myapp");
        provider = new CasAuthProvider(userService, sessionService, props, restTemplate, new ObjectMapper());
    }

    @Test
    void getLoginRedirectUrl_encodesServiceUrl() {
        var url = provider.getLoginRedirectUrl();
        assertThat(url).startsWith("https://cas.example.com/cas/tiger/myapp/login?service=");
        assertThat(url).contains("localhost%3A3000");
    }

    @Test
    void handleCallback_throwsDefinitionException_whenNoTicket() {
        assertThatThrownBy(() -> provider.handleCallback(null, null)).isInstanceOf(DefinitionException.class);
    }

    @Test
    void handleCallback_throwsDefinitionException_whenValidateFails() {
        var failureJson =
                "{\"serviceResponse\":{\"authenticationFailure\":{\"code\":\"INVALID_TICKET\",\"description\":\"Ticket expired\"}}}";
        when(restTemplate.getForObject(
                        org.mockito.ArgumentMatchers.contains("serviceValidate"),
                        org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(failureJson);

        assertThatThrownBy(() -> provider.handleCallback("ST-123", null)).isInstanceOf(DefinitionException.class);
    }

    @Test
    void handleCallback_returnsSsoUser_onSuccess() {
        var successJson =
                "{\"serviceResponse\":{\"authenticationSuccess\":{\"user\":\"john.doe\",\"attributes\":{\"email\":\"john@example.com\"}}}}";
        when(restTemplate.getForObject(
                        org.mockito.ArgumentMatchers.contains("serviceValidate"),
                        org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(successJson);

        var ssoUser = provider.handleCallback("ST-456", null);
        assertThat(ssoUser.username()).isEqualTo("john.doe");
        assertThat(ssoUser.email()).isEqualTo("john@example.com");
    }
}
