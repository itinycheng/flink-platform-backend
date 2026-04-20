# SSO Auth Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add CAS (Casdoor) and OIDC (Entra ID) SSO login to flink-platform-backend while keeping the existing `auth.type=token` login unchanged.

**Architecture:** A new `SsoAuthProvider` interface (extends `AuthProvider`) covers both login-time SSO flows (redirect + callback) and request-time session validation. `CasAuthProvider` and `OidcAuthProvider` implement it, each active via `@ConditionalOnProperty`. All three auth types share the same `X-Token` → `t_user_session` request auth mechanism — only the login acquisition path differs. Session TTL uses the existing `lastLoginTime` column with a configurable sliding window.

**Tech Stack:** Java 21, Spring Boot 3, MyBatis-Plus, RestTemplate (already wired in `RestConfig`), no new dependencies needed.

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `web/.../config/auth/SsoAuthProvider.java` | Create | Interface: `getLoginRedirectUrl`, `handleCallback` |
| `web/.../config/auth/SsoUser.java` | Create | Record: `username`, `email`, `displayName` |
| `web/.../config/auth/AbstractSessionAuthProvider.java` | Create | Shared: `authenticate()` with TTL, `createOrGetSession()` |
| `web/.../config/auth/CasAuthProvider.java` | Create | CAS 2.0 ticket validation |
| `web/.../config/auth/OidcAuthProvider.java` | Create | OIDC Authorization Code flow |
| `web/.../config/SsoAuthProperties.java` | Create | `@ConfigurationProperties("auth")` for CAS/OIDC/session config |
| `web/.../controller/SsoLoginController.java` | Create | `GET /login/sso/redirect` + `GET /login/sso/callback` |
| `web/.../service/UserProvisionService.java` | Create | Auto-create `t_user` from SSO attributes |
| `web/.../config/auth/TokenAuthProvider.java` | Modify | Remove `matchIfMissing = true` (make explicit) |
| `common/.../enums/ResponseStatus.java` | Modify | Add `SSO_AUTH_FAILED` |
| `web/.../resources/application.yml` | Modify | Add `auth.session`, `auth.sso`, `auth.cas`, `auth.oidc` blocks |
| `web/.../resources/application-dev.yml` | Modify | Add dev placeholder values |
| `web/src/test/.../auth/AbstractSessionAuthProviderTest.java` | Create | TTL logic unit tests |
| `web/src/test/.../service/UserProvisionServiceTest.java` | Create | Provision logic tests |
| `web/src/test/.../auth/CasAuthProviderTest.java` | Create | CAS callback unit tests |
| `web/src/test/.../auth/OidcAuthProviderTest.java` | Create | OIDC callback unit tests |

All paths under `web` are `flink-platform-web/src/main/java/com/flink/platform/web/`.
All paths under `common` are `flink-platform-common/src/main/java/com/flink/platform/common/`.

---

## Task 1: Config Properties + ResponseStatus

**Files:**
- Create: `flink-platform-web/src/main/java/com/flink/platform/web/config/SsoAuthProperties.java`
- Modify: `flink-platform-web/src/main/resources/application.yml`
- Modify: `flink-platform-web/src/main/resources/application-dev.yml`
- Modify: `flink-platform-common/src/main/java/com/flink/platform/common/enums/ResponseStatus.java`

- [ ] **Step 1: Create `SsoAuthProperties.java`**

```java
package com.flink.platform.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Externalized configuration for all auth modes. */
@Data
@Component
@ConfigurationProperties("auth")
public class SsoAuthProperties {

    private String type = "token";

    private Session session = new Session();

    private Sso sso = new Sso();

    private Cas cas = new Cas();

    private Oidc oidc = new Oidc();

    @Data
    public static class Session {
        /** 0 = never expire. >0 = sliding window in days. */
        private int ttlDays = 0;
    }

    @Data
    public static class Sso {
        /** JSON string matching UserRoles structure, used when auto-provisioning SSO users. */
        private String defaultRoles = "{}";
    }

    @Data
    public static class Cas {
        /** Full CAS server URL including app path, e.g. https://host/cas/tiger/app-name */
        private String serverUrl;
        /** This app's callback URL, e.g. http://localhost:9104/login/sso/callback */
        private String serviceUrl;
    }

    @Data
    public static class Oidc {
        /** OIDC issuer base URL. Discovery at {authority}/.well-known/openid-configuration */
        private String authority;
        private String clientId;
        private String clientSecret;
        /** This app's callback URL, e.g. http://localhost:9104/login/sso/callback */
        private String redirectUri;
        private String scope = "openid email profile";
    }
}
```

- [ ] **Step 2: Add config blocks to `application.yml`**

Add after the existing `auth:` block:

```yaml
auth:
  type: token   # Options: token | cas | oidc
  session:
    ttl-days: 0       # 0 = never expire; >0 = sliding window in days
  sso:
    default-roles: '{}' # JSON UserRoles for auto-provisioned SSO users
  cas:
    server-url:       # e.g. https://ams-cas-cn-test.tigerbrokers.net/cas/tiger/flink-platform
    service-url:      # e.g. http://localhost:9104/login/sso/callback
  oidc:
    authority:        # e.g. https://login.microsoftonline.com/{tenant-id}/v2.0
    client-id:        # OAuth2 client ID
    client-secret:    # OAuth2 client secret — use ${OIDC_CLIENT_SECRET} in prod
    redirect-uri:     # e.g. http://localhost:9104/login/sso/callback
    scope: openid email profile
```

- [ ] **Step 3: Add dev placeholder values to `application-dev.yml`**

```yaml
# SSO is disabled in dev by default (auth.type=token). Uncomment to test SSO.
# auth:
#   type: cas
#   cas:
#     server-url: https://ams-cas-cn-test.tigerbrokers.net/cas/tiger/flink-platform
#     service-url: http://localhost:9104/login/sso/callback
```

- [ ] **Step 4: Add `SSO_AUTH_FAILED` to `ResponseStatus`**

In `flink-platform-common/src/main/java/com/flink/platform/common/enums/ResponseStatus.java`, add before the `;`:

```java
    SSO_AUTH_FAILED(10033, "SSO authentication failed"),
```

- [ ] **Step 5: Run build to verify no compile errors**

```bash
./mvnw compile -pl flink-platform-common,flink-platform-web -Dskip.frontend=true -q
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add flink-platform-web/src/main/java/com/flink/platform/web/config/SsoAuthProperties.java \
        flink-platform-web/src/main/resources/application.yml \
        flink-platform-web/src/main/resources/application-dev.yml \
        flink-platform-common/src/main/java/com/flink/platform/common/enums/ResponseStatus.java
git commit -m "feat: add SSO auth config properties and SSO_AUTH_FAILED status"
```

---

## Task 2: `SsoUser` record + `SsoAuthProvider` interface

**Files:**
- Create: `flink-platform-web/src/main/java/com/flink/platform/web/config/auth/SsoUser.java`
- Create: `flink-platform-web/src/main/java/com/flink/platform/web/config/auth/SsoAuthProvider.java`

- [ ] **Step 1: Create `SsoUser.java`**

```java
package com.flink.platform.web.config.auth;

/** User attributes returned by an SSO provider after successful authentication. */
public record SsoUser(String username, String email, String displayName) {}
```

- [ ] **Step 2: Create `SsoAuthProvider.java`**

```java
package com.flink.platform.web.config.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Extension of {@link AuthProvider} for SSO-based login flows (CAS, OIDC).
 * Implementations are selected via {@code auth.type}.
 */
public interface SsoAuthProvider extends AuthProvider {

    /**
     * Returns the URL to redirect the browser to for SSO login.
     *
     * @param callbackUrl this app's callback URL (must match registered redirect URI)
     */
    String getLoginRedirectUrl(String callbackUrl);

    /**
     * Validates the SSO callback (CAS ticket or OIDC code) and returns the authenticated user.
     * Throws {@link com.flink.platform.common.exception.DefinitionException} on failure.
     *
     * @param request     the callback HTTP request (contains ticket/code as query params)
     * @param callbackUrl the same callbackUrl passed to {@link #getLoginRedirectUrl}
     */
    SsoUser handleCallback(HttpServletRequest request, String callbackUrl);
}
```

- [ ] **Step 3: Compile**

```bash
./mvnw compile -pl flink-platform-web -Dskip.frontend=true -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add flink-platform-web/src/main/java/com/flink/platform/web/config/auth/SsoUser.java \
        flink-platform-web/src/main/java/com/flink/platform/web/config/auth/SsoAuthProvider.java
git commit -m "feat: add SsoAuthProvider interface and SsoUser record"
```

---

## Task 3: `AbstractSessionAuthProvider` + fix `TokenAuthProvider`

**Files:**
- Create: `flink-platform-web/src/main/java/com/flink/platform/web/config/auth/AbstractSessionAuthProvider.java`
- Create: `flink-platform-web/src/test/java/com/flink/platform/web/auth/AbstractSessionAuthProviderTest.java`
- Modify: `flink-platform-web/src/main/java/com/flink/platform/web/config/auth/TokenAuthProvider.java`

- [ ] **Step 1: Write failing tests for TTL logic**

Create `flink-platform-web/src/test/java/com/flink/platform/web/auth/AbstractSessionAuthProviderTest.java`:

```java
package com.flink.platform.web.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.dao.entity.Session;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.SessionService;
import com.flink.platform.dao.service.UserService;
import com.flink.platform.web.config.SsoAuthProperties;
import com.flink.platform.web.config.auth.AbstractSessionAuthProvider;
import com.flink.platform.web.config.auth.SsoUser;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractSessionAuthProviderTest {

    @Mock private UserService userService;
    @Mock private SessionService sessionService;
    @Mock private HttpServletRequest request;

    private SsoAuthProperties props;
    private TestableProvider provider;

    // Concrete subclass for testing the abstract class
    static class TestableProvider extends AbstractSessionAuthProvider {
        TestableProvider(UserService u, SessionService s, SsoAuthProperties p) {
            super(u, s, p);
        }
        @Override public String getLoginRedirectUrl(String cb) { return ""; }
        @Override public SsoUser handleCallback(HttpServletRequest req, String cb) { return null; }
    }

    @BeforeEach
    void setUp() {
        props = new SsoAuthProperties();
        provider = new TestableProvider(userService, sessionService, props);
    }

    @Test
    void authenticate_returnsNull_whenNoXToken() {
        when(request.getHeader("X-Token")).thenReturn(null);
        assertThat(provider.authenticate(request)).isNull();
    }

    @Test
    void authenticate_returnsNull_whenSessionNotFound() {
        when(request.getHeader("X-Token")).thenReturn("tok123");
        when(sessionService.getOne(any(QueryWrapper.class))).thenReturn(null);
        assertThat(provider.authenticate(request)).isNull();
    }

    @Test
    void authenticate_returnsUser_whenTtlDisabled() {
        props.getSession().setTtlDays(0);
        var session = sessionWithAge(30);
        var user = new User();
        when(request.getHeader("X-Token")).thenReturn("tok");
        when(sessionService.getOne(any(QueryWrapper.class))).thenReturn(session);
        when(userService.getById(session.getUserId())).thenReturn(user);

        assertThat(provider.authenticate(request)).isSameAs(user);
        verify(sessionService, never()).removeById(any());
        verify(sessionService, never()).updateById(any());
    }

    @Test
    void authenticate_deletesSession_whenExpired() {
        props.getSession().setTtlDays(7);
        var session = sessionWithAge(8); // 8 days old → expired
        when(request.getHeader("X-Token")).thenReturn("tok");
        when(sessionService.getOne(any(QueryWrapper.class))).thenReturn(session);

        assertThat(provider.authenticate(request)).isNull();
        verify(sessionService).removeById(session.getId());
    }

    @Test
    void authenticate_returnsUserAndUpdatesLastLogin_whenWithinTtl() {
        props.getSession().setTtlDays(7);
        var session = sessionWithAge(3); // 3 days old → valid
        var user = new User();
        when(request.getHeader("X-Token")).thenReturn("tok");
        when(sessionService.getOne(any(QueryWrapper.class))).thenReturn(session);
        when(userService.getById(session.getUserId())).thenReturn(user);

        assertThat(provider.authenticate(request)).isSameAs(user);

        var captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionService).updateById(captor.capture());
        assertThat(captor.getValue().getLastLoginTime()).isAfter(LocalDateTime.now().minusMinutes(1));
    }

    private Session sessionWithAge(int daysOld) {
        var s = new Session();
        s.setId(1L);
        s.setToken("tok");
        s.setUserId(42L);
        s.setLastLoginTime(LocalDateTime.now().minusDays(daysOld));
        return s;
    }
}
```

- [ ] **Step 2: Run tests — expect compile failure (class doesn't exist yet)**

```bash
./mvnw test -pl flink-platform-web -Dtest=AbstractSessionAuthProviderTest -Dskip.frontend=true 2>&1 | tail -5
```

Expected: compilation error — `AbstractSessionAuthProvider` not found

- [ ] **Step 3: Create `AbstractSessionAuthProvider.java`**

```java
package com.flink.platform.web.config.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.dao.entity.Session;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.SessionService;
import com.flink.platform.dao.service.UserService;
import com.flink.platform.web.config.SsoAuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base for SSO auth providers. Handles X-Token → t_user_session validation (with optional TTL)
 * and session creation. Subclasses implement the SSO-specific login flow.
 */
@Slf4j
public abstract class AbstractSessionAuthProvider implements SsoAuthProvider {

    protected final UserService userService;
    protected final SessionService sessionService;
    protected final SsoAuthProperties props;

    protected AbstractSessionAuthProvider(
            UserService userService, SessionService sessionService, SsoAuthProperties props) {
        this.userService = userService;
        this.sessionService = sessionService;
        this.props = props;
    }

    @Override
    public @Nullable User authenticate(HttpServletRequest request) {
        var token = request.getHeader("X-Token");
        if (StringUtils.isEmpty(token)) {
            return null;
        }

        var session = sessionService.getOne(
                new QueryWrapper<Session>().lambda().eq(Session::getToken, token));
        if (session == null) {
            log.info("Session not found for token.");
            return null;
        }

        int ttlDays = props.getSession().getTtlDays();
        if (ttlDays > 0) {
            var expiry = session.getLastLoginTime().plusDays(ttlDays);
            if (LocalDateTime.now().isAfter(expiry)) {
                log.info("Session expired for userId: {}", session.getUserId());
                sessionService.removeById(session.getId());
                return null;
            }
            // Sliding window: refresh lastLoginTime on each valid request
            session.setLastLoginTime(LocalDateTime.now());
            sessionService.updateById(session);
        }

        var user = userService.getById(session.getUserId());
        if (user == null) {
            log.info("User not found for session userId: {}", session.getUserId());
        }
        return user;
    }

    /**
     * Creates a new session for the given user, or returns the existing one for the same IP.
     * Returns a map with "token" and the session, suitable for the login response.
     */
    protected Session createOrGetSession(long userId, String ip) {
        var existing = sessionService.getOne(new QueryWrapper<Session>().lambda()
                .eq(Session::getUserId, userId)
                .eq(Session::getIp, ip));
        if (existing != null) {
            existing.setLastLoginTime(LocalDateTime.now());
            sessionService.updateById(existing);
            return existing;
        }
        var session = new Session();
        session.setToken(UUID.randomUUID().toString().replace("-", ""));
        session.setUserId(userId);
        session.setIp(ip);
        session.setLastLoginTime(LocalDateTime.now());
        sessionService.save(session);
        return session;
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
./mvnw test -pl flink-platform-web -Dtest=AbstractSessionAuthProviderTest -Dskip.frontend=true 2>&1 | tail -10
```

Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 5: Fix `TokenAuthProvider` — remove `matchIfMissing = true`**

In `TokenAuthProvider.java` change line 20:

```java
// Before:
@ConditionalOnProperty(name = "auth.type", havingValue = "token", matchIfMissing = true)

// After:
@ConditionalOnProperty(name = "auth.type", havingValue = "token", matchIfMissing = true)
```

Wait — `matchIfMissing = true` is intentional here to keep `token` as the default when `auth.type` is not set. Leave `TokenAuthProvider` exactly as-is. It only activates when `auth.type=token` (or unset). When `auth.type=cas` or `auth.type=oidc`, it won't activate, and the corresponding `CasAuthProvider`/`OidcAuthProvider` will be active instead.

- [ ] **Step 6: Commit**

```bash
git add flink-platform-web/src/main/java/com/flink/platform/web/config/auth/AbstractSessionAuthProvider.java \
        flink-platform-web/src/test/java/com/flink/platform/web/auth/AbstractSessionAuthProviderTest.java
git commit -m "feat: add AbstractSessionAuthProvider with TTL sliding-window session validation"
```

---

## Task 4: `UserProvisionService`

**Files:**
- Create: `flink-platform-web/src/main/java/com/flink/platform/web/service/UserProvisionService.java`
- Create: `flink-platform-web/src/test/java/com/flink/platform/web/service/UserProvisionServiceTest.java`

- [ ] **Step 1: Write failing tests**

Create `flink-platform-web/src/test/java/com/flink/platform/web/service/UserProvisionServiceTest.java`:

```java
package com.flink.platform.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flink.platform.common.enums.UserStatus;
import com.flink.platform.common.model.UserRoles;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.UserService;
import com.flink.platform.web.config.SsoAuthProperties;
import com.flink.platform.web.config.auth.SsoUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProvisionServiceTest {

    @Mock private UserService userService;

    private SsoAuthProperties props;
    private UserProvisionService service;

    @BeforeEach
    void setUp() {
        props = new SsoAuthProperties();
        service = new UserProvisionService(userService, new ObjectMapper(), props);
    }

    @Test
    void provision_returnsExistingUser_whenUsernameExists() {
        var ssoUser = new SsoUser("alice", "alice@example.com", "Alice");
        var existing = new User();
        existing.setUsername("alice");
        when(userService.getOne(any(QueryWrapper.class))).thenReturn(existing);

        assertThat(service.provision(ssoUser)).isSameAs(existing);
        verify(userService, never()).save(any());
    }

    @Test
    void provision_createsNewUser_whenNotFound() {
        var ssoUser = new SsoUser("bob", "bob@example.com", "Bob");
        when(userService.getOne(any(QueryWrapper.class))).thenReturn(null);

        service.provision(ssoUser);

        var captor = ArgumentCaptor.forClass(User.class);
        verify(userService).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("bob");
        assertThat(saved.getEmail()).isEqualTo("bob@example.com");
        assertThat(saved.getPassword()).isNull();
        assertThat(saved.getStatus()).isEqualTo(UserStatus.NORMAL);
    }

    @Test
    void provision_appliesDefaultRoles_whenConfigured() {
        props.getSso().setDefaultRoles("{\"global\":\"VIEWER\"}");
        var ssoUser = new SsoUser("carol", null, null);
        when(userService.getOne(any(QueryWrapper.class))).thenReturn(null);

        service.provision(ssoUser);

        var captor = ArgumentCaptor.forClass(User.class);
        verify(userService).save(captor.capture());
        assertThat(captor.getValue().getRoles().getGlobal()).isNotNull();
    }
}
```

- [ ] **Step 2: Run tests — expect compile failure**

```bash
./mvnw test -pl flink-platform-web -Dtest=UserProvisionServiceTest -Dskip.frontend=true 2>&1 | tail -5
```

Expected: compilation error

- [ ] **Step 3: Create `UserProvisionService.java`**

```java
package com.flink.platform.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flink.platform.common.enums.UserStatus;
import com.flink.platform.common.model.UserRoles;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.UserService;
import com.flink.platform.web.config.SsoAuthProperties;
import com.flink.platform.web.config.auth.SsoUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Auto-provisions a local {@link User} from SSO attributes on first login. */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class UserProvisionService {

    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final SsoAuthProperties props;

    /**
     * Returns the existing user matching {@code ssoUser.username()}, or creates a new one.
     */
    public User provision(SsoUser ssoUser) {
        var existing = userService.getOne(new QueryWrapper<User>().lambda()
                .eq(User::getUsername, ssoUser.username()));
        if (existing != null) {
            return existing;
        }

        var user = new User();
        user.setUsername(ssoUser.username());
        user.setEmail(ssoUser.email());
        user.setPassword(null); // SSO users have no local password
        user.setStatus(UserStatus.NORMAL);
        user.setRoles(parseDefaultRoles());
        userService.save(user);
        log.info("Auto-provisioned SSO user: {}", ssoUser.username());
        return user;
    }

    private UserRoles parseDefaultRoles() {
        String json = props.getSso().getDefaultRoles();
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return new UserRoles();
        }
        try {
            return objectMapper.readValue(json, UserRoles.class);
        } catch (Exception e) {
            log.warn("Failed to parse auth.sso.default-roles '{}', using empty roles: {}", json, e.getMessage());
            return new UserRoles();
        }
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
./mvnw test -pl flink-platform-web -Dtest=UserProvisionServiceTest -Dskip.frontend=true 2>&1 | tail -10
```

Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add flink-platform-web/src/main/java/com/flink/platform/web/service/UserProvisionService.java \
        flink-platform-web/src/test/java/com/flink/platform/web/service/UserProvisionServiceTest.java
git commit -m "feat: add UserProvisionService for SSO user auto-provisioning"
```

---

## Task 5: `CasAuthProvider`

**Files:**
- Create: `flink-platform-web/src/main/java/com/flink/platform/web/config/auth/CasAuthProvider.java`
- Create: `flink-platform-web/src/test/java/com/flink/platform/web/auth/CasAuthProviderTest.java`

- [ ] **Step 1: Write failing tests**

Create `flink-platform-web/src/test/java/com/flink/platform/web/auth/CasAuthProviderTest.java`:

```java
package com.flink.platform.web.auth;

import com.flink.platform.common.exception.DefinitionException;
import com.flink.platform.dao.service.SessionService;
import com.flink.platform.dao.service.UserService;
import com.flink.platform.web.config.SsoAuthProperties;
import com.flink.platform.web.config.auth.CasAuthProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CasAuthProviderTest {

    @Mock private UserService userService;
    @Mock private SessionService sessionService;
    @Mock private RestTemplate restTemplate;
    @Mock private HttpServletRequest request;

    private SsoAuthProperties props;
    private CasAuthProvider provider;

    @BeforeEach
    void setUp() {
        props = new SsoAuthProperties();
        props.getCas().setServerUrl("https://cas.example.com/cas/tiger/myapp");
        props.getCas().setServiceUrl("http://localhost:9104/login/sso/callback");
        provider = new CasAuthProvider(userService, sessionService, props, restTemplate);
    }

    @Test
    void getLoginRedirectUrl_encodesServiceUrl() {
        var url = provider.getLoginRedirectUrl("http://localhost:9104/login/sso/callback");
        assertThat(url).startsWith("https://cas.example.com/cas/tiger/myapp/login?service=");
        assertThat(url).contains("localhost%3A9104");
    }

    @Test
    void handleCallback_throwsDefinitionException_whenNoTicket() {
        when(request.getParameter("ticket")).thenReturn(null);
        assertThatThrownBy(() -> provider.handleCallback(request, "http://localhost:9104/login/sso/callback"))
                .isInstanceOf(DefinitionException.class);
    }

    @Test
    void handleCallback_throwsDefinitionException_whenValidateFails() {
        when(request.getParameter("ticket")).thenReturn("ST-123");
        var failureJson = "{\"serviceResponse\":{\"authenticationFailure\":{\"code\":\"INVALID_TICKET\",\"description\":\"Ticket expired\"}}}";
        when(restTemplate.getForObject(
                org.mockito.ArgumentMatchers.contains("serviceValidate"),
                org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(failureJson);

        assertThatThrownBy(() -> provider.handleCallback(request, "http://localhost:9104/login/sso/callback"))
                .isInstanceOf(DefinitionException.class);
    }

    @Test
    void handleCallback_returnsSsoUser_onSuccess() {
        when(request.getParameter("ticket")).thenReturn("ST-456");
        var successJson = "{\"serviceResponse\":{\"authenticationSuccess\":{\"user\":\"john.doe\",\"attributes\":{\"email\":\"john@example.com\"}}}}";
        when(restTemplate.getForObject(
                org.mockito.ArgumentMatchers.contains("serviceValidate"),
                org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(successJson);

        var ssoUser = provider.handleCallback(request, "http://localhost:9104/login/sso/callback");
        assertThat(ssoUser.username()).isEqualTo("john.doe");
        assertThat(ssoUser.email()).isEqualTo("john@example.com");
    }
}
```

- [ ] **Step 2: Run tests — expect compile failure**

```bash
./mvnw test -pl flink-platform-web -Dtest=CasAuthProviderTest -Dskip.frontend=true 2>&1 | tail -5
```

Expected: compilation error

- [ ] **Step 3: Create `CasAuthProvider.java`**

```java
package com.flink.platform.web.config.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flink.platform.common.exception.DefinitionException;
import com.flink.platform.dao.service.SessionService;
import com.flink.platform.dao.service.UserService;
import com.flink.platform.web.config.SsoAuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static com.flink.platform.common.enums.ResponseStatus.SSO_AUTH_FAILED;

/**
 * CAS 2.0 authentication provider. Active when {@code auth.type=cas}.
 * Implements the standard Apereo CAS protocol (login redirect + serviceValidate).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "auth.type", havingValue = "cas")
public class CasAuthProvider extends AbstractSessionAuthProvider {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public CasAuthProvider(
            UserService userService,
            SessionService sessionService,
            SsoAuthProperties props,
            RestTemplate restTemplate) {
        super(userService, sessionService, props);
        this.restTemplate = restTemplate;
    }

    @Override
    public String getLoginRedirectUrl(String callbackUrl) {
        var encoded = URLEncoder.encode(callbackUrl, StandardCharsets.UTF_8);
        return props.getCas().getServerUrl() + "/login?service=" + encoded;
    }

    @Override
    public SsoUser handleCallback(HttpServletRequest request, String callbackUrl) {
        var ticket = request.getParameter("ticket");
        if (StringUtils.isEmpty(ticket)) {
            throw new DefinitionException(SSO_AUTH_FAILED);
        }

        var validateUrl = UriComponentsBuilder
                .fromUriString(props.getCas().getServerUrl() + "/serviceValidate")
                .queryParam("ticket", ticket)
                .queryParam("service", callbackUrl)
                .queryParam("format", "json")
                .toUriString();

        String response;
        try {
            response = restTemplate.getForObject(validateUrl, String.class);
        } catch (Exception e) {
            log.error("CAS serviceValidate request failed", e);
            throw new DefinitionException(SSO_AUTH_FAILED);
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode success = root.path("serviceResponse").path("authenticationSuccess");
            if (success.isMissingNode()) {
                JsonNode failure = root.path("serviceResponse").path("authenticationFailure");
                log.warn("CAS authentication failed: {}", failure);
                throw new DefinitionException(SSO_AUTH_FAILED);
            }
            String username = success.path("user").asText();
            String email = success.path("attributes").path("email").asText(null);
            return new SsoUser(username, email, null);
        } catch (DefinitionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse CAS serviceValidate response", e);
            throw new DefinitionException(SSO_AUTH_FAILED);
        }
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
./mvnw test -pl flink-platform-web -Dtest=CasAuthProviderTest -Dskip.frontend=true 2>&1 | tail -10
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add flink-platform-web/src/main/java/com/flink/platform/web/config/auth/CasAuthProvider.java \
        flink-platform-web/src/test/java/com/flink/platform/web/auth/CasAuthProviderTest.java
git commit -m "feat: add CasAuthProvider for CAS 2.0 SSO login"
```

---

## Task 6: `OidcAuthProvider`

**Files:**
- Create: `flink-platform-web/src/main/java/com/flink/platform/web/config/auth/OidcAuthProvider.java`
- Create: `flink-platform-web/src/test/java/com/flink/platform/web/auth/OidcAuthProviderTest.java`

- [ ] **Step 1: Write failing tests**

Create `flink-platform-web/src/test/java/com/flink/platform/web/auth/OidcAuthProviderTest.java`:

```java
package com.flink.platform.web.auth;

import com.flink.platform.common.exception.DefinitionException;
import com.flink.platform.dao.service.SessionService;
import com.flink.platform.dao.service.UserService;
import com.flink.platform.web.config.SsoAuthProperties;
import com.flink.platform.web.config.auth.OidcAuthProvider;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OidcAuthProviderTest {

    @Mock private UserService userService;
    @Mock private SessionService sessionService;
    @Mock private RestTemplate restTemplate;
    @Mock private HttpServletRequest request;

    private SsoAuthProperties props;
    private OidcAuthProvider provider;

    // Minimal OIDC discovery document
    private static final String DISCOVERY_JSON = """
            {"authorization_endpoint":"https://login.example.com/authorize",
             "token_endpoint":"https://login.example.com/token"}
            """;

    @BeforeEach
    void setUp() {
        props = new SsoAuthProperties();
        props.getOidc().setAuthority("https://login.example.com");
        props.getOidc().setClientId("client-id");
        props.getOidc().setClientSecret("secret");
        props.getOidc().setRedirectUri("http://localhost:9104/login/sso/callback");
        props.getOidc().setScope("openid email profile");

        when(restTemplate.getForObject(
                eq("https://login.example.com/.well-known/openid-configuration"),
                eq(String.class)))
                .thenReturn(DISCOVERY_JSON);

        provider = new OidcAuthProvider(userService, sessionService, props, restTemplate);
    }

    @Test
    void getLoginRedirectUrl_containsRequiredParams() {
        var url = provider.getLoginRedirectUrl("http://localhost:9104/login/sso/callback");
        assertThat(url).startsWith("https://login.example.com/authorize");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("client_id=client-id");
        assertThat(url).contains("state=");
    }

    @Test
    void handleCallback_throwsDefinitionException_whenNoCode() {
        when(request.getParameter("code")).thenReturn(null);
        assertThatThrownBy(() -> provider.handleCallback(request, "http://localhost:9104/login/sso/callback"))
                .isInstanceOf(DefinitionException.class);
    }

    @Test
    void handleCallback_throwsDefinitionException_whenStateInvalid() {
        when(request.getParameter("code")).thenReturn("auth-code");
        when(request.getParameter("state")).thenReturn("invalid-state-not-in-cache");
        assertThatThrownBy(() -> provider.handleCallback(request, "http://localhost:9104/login/sso/callback"))
                .isInstanceOf(DefinitionException.class);
    }

    @Test
    void handleCallback_returnsSsoUser_onSuccess() {
        // Prime the state cache by calling getLoginRedirectUrl first
        var redirectUrl = provider.getLoginRedirectUrl("http://localhost:9104/login/sso/callback");
        var state = redirectUrl.replaceAll(".*state=([^&]+).*", "$1");

        // Build a minimal ID token: header.payload.signature (signature ignored)
        var payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"preferred_username\":\"jane.doe\",\"email\":\"jane@example.com\"}".getBytes());
        var idToken = "header." + payload + ".sig";
        var tokenResponseJson = "{\"id_token\":\"" + idToken + "\",\"access_token\":\"at\"}";

        when(request.getParameter("code")).thenReturn("auth-code");
        when(request.getParameter("state")).thenReturn(state);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(tokenResponseJson, HttpStatus.OK));

        var ssoUser = provider.handleCallback(request, "http://localhost:9104/login/sso/callback");
        assertThat(ssoUser.username()).isEqualTo("jane.doe");
        assertThat(ssoUser.email()).isEqualTo("jane@example.com");
    }
}
```

- [ ] **Step 2: Run tests — expect compile failure**

```bash
./mvnw test -pl flink-platform-web -Dtest=OidcAuthProviderTest -Dskip.frontend=true 2>&1 | tail -5
```

Expected: compilation error

- [ ] **Step 3: Create `OidcAuthProvider.java`**

```java
package com.flink.platform.web.config.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flink.platform.common.exception.DefinitionException;
import com.flink.platform.dao.service.SessionService;
import com.flink.platform.dao.service.UserService;
import com.flink.platform.web.config.SsoAuthProperties;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
public class OidcAuthProvider extends AbstractSessionAuthProvider {

    private static final int STATE_TTL_SECONDS = 300;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Discovered endpoints, cached at startup. */
    private String authorizationEndpoint;
    private String tokenEndpoint;

    /** Short-lived state store for CSRF protection: state → expiry. */
    private final ConcurrentHashMap<String, Instant> stateCache = new ConcurrentHashMap<>();

    @Autowired
    public OidcAuthProvider(
            UserService userService,
            SessionService sessionService,
            SsoAuthProperties props,
            RestTemplate restTemplate) {
        super(userService, sessionService, props);
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    void discoverEndpoints() {
        var discoveryUrl = props.getOidc().getAuthority() + "/.well-known/openid-configuration";
        try {
            String json = restTemplate.getForObject(discoveryUrl, String.class);
            JsonNode node = objectMapper.readTree(json);
            authorizationEndpoint = node.path("authorization_endpoint").asText();
            tokenEndpoint = node.path("token_endpoint").asText();
            log.info("OIDC discovery complete. auth={} token={}", authorizationEndpoint, tokenEndpoint);
        } catch (Exception e) {
            throw new IllegalStateException("OIDC discovery failed for " + discoveryUrl, e);
        }
    }

    @Override
    public String getLoginRedirectUrl(String callbackUrl) {
        var state = UUID.randomUUID().toString();
        stateCache.put(state, Instant.now().plusSeconds(STATE_TTL_SECONDS));

        return UriComponentsBuilder.fromUriString(authorizationEndpoint)
                .queryParam("response_type", "code")
                .queryParam("client_id", props.getOidc().getClientId())
                .queryParam("redirect_uri", URLEncoder.encode(callbackUrl, StandardCharsets.UTF_8))
                .queryParam("scope", URLEncoder.encode(props.getOidc().getScope(), StandardCharsets.UTF_8))
                .queryParam("state", state)
                .build(false)
                .toUriString();
    }

    @Override
    public SsoUser handleCallback(HttpServletRequest request, String callbackUrl) {
        var code = request.getParameter("code");
        if (StringUtils.isEmpty(code)) {
            log.warn("OIDC callback missing 'code' parameter");
            throw new DefinitionException(SSO_AUTH_FAILED);
        }

        var state = request.getParameter("state");
        if (!isValidState(state)) {
            log.warn("OIDC callback invalid or expired state: {}", state);
            throw new DefinitionException(SSO_AUTH_FAILED);
        }
        stateCache.remove(state);

        var tokenResponse = exchangeCode(code, callbackUrl);
        return extractUser(tokenResponse);
    }

    private boolean isValidState(String state) {
        if (StringUtils.isEmpty(state)) return false;
        var expiry = stateCache.get(state);
        if (expiry == null) return false;
        return Instant.now().isBefore(expiry);
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
            var response = restTemplate.postForEntity(
                    tokenEndpoint, new HttpEntity<>(body, headers), String.class);
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
            // Prefer id_token for user info (access_token may not contain email per Entra ID docs)
            String idToken = root.path("id_token").asText(null);
            if (StringUtils.isEmpty(idToken)) {
                log.error("OIDC token response missing id_token");
                throw new DefinitionException(SSO_AUTH_FAILED);
            }

            // Decode JWT payload (middle part) — no signature verification needed;
            // token comes directly from the trusted token endpoint, not from the user.
            String[] parts = idToken.split("\\.");
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> claims = objectMapper.readValue(payloadBytes, new TypeReference<>() {});

            // preferred_username is set by Entra ID; fall back to email
            String username = (String) claims.getOrDefault("preferred_username", claims.get("email"));
            String email = (String) claims.get("email");

            if (StringUtils.isEmpty(username)) {
                log.error("OIDC id_token missing preferred_username and email claims");
                throw new DefinitionException(SSO_AUTH_FAILED);
            }
            return new SsoUser(username, email, (String) claims.get("name"));
        } catch (DefinitionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse OIDC id_token", e);
            throw new DefinitionException(SSO_AUTH_FAILED);
        }
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
./mvnw test -pl flink-platform-web -Dtest=OidcAuthProviderTest -Dskip.frontend=true 2>&1 | tail -10
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add flink-platform-web/src/main/java/com/flink/platform/web/config/auth/OidcAuthProvider.java \
        flink-platform-web/src/test/java/com/flink/platform/web/auth/OidcAuthProviderTest.java
git commit -m "feat: add OidcAuthProvider for OIDC Authorization Code SSO login"
```

---

## Task 7: `SsoLoginController`

**Files:**
- Create: `flink-platform-web/src/main/java/com/flink/platform/web/controller/SsoLoginController.java`

No unit test for the controller — it's a thin orchestration layer; the logic under test lives in providers and services. Integration-test manually after wiring.

- [ ] **Step 1: Create `SsoLoginController.java`**

```java
package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.dao.entity.Workspace;
import com.flink.platform.dao.service.WorkspaceService;
import com.flink.platform.web.config.auth.SsoAuthProvider;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.service.UserProvisionService;
import com.flink.platform.web.util.HttpUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

import static com.flink.platform.common.enums.Role.SUPER_ADMIN;
import static com.flink.platform.common.enums.Status.ENABLE;
import static com.flink.platform.web.entity.response.ResultInfo.success;

/**
 * SSO login endpoints. Active only when a {@link SsoAuthProvider} bean is registered
 * (i.e. when {@code auth.type=cas} or {@code auth.type=oidc}).
 */
@Slf4j
@RestController
@RequestMapping("/login/sso")
@ConditionalOnBean(SsoAuthProvider.class)
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class SsoLoginController {

    private final SsoAuthProvider ssoAuthProvider;
    private final UserProvisionService userProvisionService;
    private final WorkspaceService workspaceService;

    /**
     * Returns the SSO provider's login URL. The frontend should redirect the browser to it.
     *
     * @param callbackUrl optional override; defaults to {@code {request base URL}/login/sso/callback}
     */
    @GetMapping("/redirect")
    public ResultInfo<String> redirect(
            HttpServletRequest request,
            @RequestParam(required = false) String callbackUrl) {
        var cb = callbackUrl != null ? callbackUrl : buildCallbackUrl(request);
        return success(ssoAuthProvider.getLoginRedirectUrl(cb));
    }

    /**
     * Handles the SSO provider's callback. Validates the ticket/code, provisions the user,
     * creates a session, and returns {@code {token, workspaceId}}.
     */
    @GetMapping("/callback")
    public ResultInfo<Map<String, Object>> callback(
            HttpServletRequest request,
            @RequestParam(required = false) String callbackUrl) {
        var cb = callbackUrl != null ? callbackUrl : buildCallbackUrl(request);
        var ssoUser = ssoAuthProvider.handleCallback(request, cb);
        var user = userProvisionService.provision(ssoUser);
        var clientIp = HttpUtil.getClientIpAddress(request);
        var session = ssoAuthProvider.createOrGetSession(user.getId(), clientIp);

        var userRoles = user.getRoles();
        Long workspaceId = userRoles != null ? userRoles.getAnyWorkspaceId() : null;

        if (workspaceId == null && userRoles != null && SUPER_ADMIN.equals(userRoles.getGlobal())) {
            var ws = workspaceService.getOne(new QueryWrapper<Workspace>().lambda()
                    .eq(Workspace::getStatus, ENABLE)
                    .last("limit 1"));
            if (ws != null) {
                workspaceId = ws.getId();
            }
        }

        var result = new HashMap<String, Object>(2);
        result.put("token", session.getToken());
        result.put("workspaceId", workspaceId);
        return success(result);
    }

    private String buildCallbackUrl(HttpServletRequest request) {
        return request.getScheme() + "://" + request.getServerName()
                + ":" + request.getServerPort() + "/login/sso/callback";
    }
}
```

- [ ] **Step 2: Compile**

```bash
./mvnw compile -pl flink-platform-web -Dskip.frontend=true -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add flink-platform-web/src/main/java/com/flink/platform/web/controller/SsoLoginController.java
git commit -m "feat: add SsoLoginController for SSO redirect and callback endpoints"
```

---

## Task 8: Full build + all tests

- [ ] **Step 1: Run all web module tests**

```bash
./mvnw test -pl flink-platform-web -Dskip.frontend=true 2>&1 | tail -20
```

Expected: All tests pass, no failures.

- [ ] **Step 2: Run full build (skip tests for speed)**

```bash
./mvnw clean package -DskipTests -Dskip.frontend=true 2>&1 | tail -10
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Verify `auth.type=token` still works (smoke test)**

Start the app with the dev profile and verify `/login` still works:

```bash
# In one terminal:
java -jar flink-platform-web/target/flink-platform-web-0.0.1.jar \
  -Dspring.profiles.active=dev 2>&1 | grep -E "Started|ERROR" &

# In another terminal (after app starts):
curl -s -X POST http://localhost:9104/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | python3 -m json.tool
```

Expected: JSON response with `token` and `workspaceId`.

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "feat: SSO auth integration complete (CAS + OIDC)"
```

---

## Spec Coverage Check

| Spec requirement | Implemented in |
|---|---|
| `auth.type=cas` CAS 2.0 login | Task 5 `CasAuthProvider` |
| `auth.type=oidc` OIDC auth code flow | Task 6 `OidcAuthProvider` |
| `auth.type=token` unchanged | TokenAuthProvider untouched |
| No table schema changes | `createOrGetSession` reuses existing columns |
| Auto-provision `t_user` | Task 4 `UserProvisionService` |
| Session TTL sliding window | Task 3 `AbstractSessionAuthProvider.authenticate()` |
| All URLs in config (no hardcodes) | Task 1 `SsoAuthProperties` |
| Generic code (no company coupling) | Standard CAS/OIDC protocols only |
| `GET /login/sso/redirect` | Task 7 `SsoLoginController.redirect()` |
| `GET /login/sso/callback` | Task 7 `SsoLoginController.callback()` |
| OIDC state CSRF protection | Task 6 `stateCache` in `OidcAuthProvider` |
| OIDC discovery cached at startup | Task 6 `@PostConstruct discoverEndpoints()` |
