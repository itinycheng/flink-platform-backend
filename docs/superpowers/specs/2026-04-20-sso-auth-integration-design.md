# SSO Auth Integration Design

**Date:** 2026-04-20
**Branch:** feature/permission-system

---

## Background

flink-platform-backend currently uses a simple username/password login (`auth.type=token`). The company provides two SSO options for internal systems:

- **Casdoor** — CAS 2.0/3.0 protocol (also supports OIDC)
- **Microsoft Entra ID (Azure AD)** — OIDC / OAuth2 Authorization Code flow

Both are required. All URLs must be externalized to config. Code must be generic (standard protocols only, no company-specific coupling).

---

## Goals

1. Add `auth.type=cas` — Casdoor CAS 2.0 login
2. Add `auth.type=oidc` — Entra ID (or any OIDC provider) login
3. Keep `auth.type=token` (existing username/password) fully unchanged
4. No table schema changes (`t_user` and `t_user_session` unchanged)
5. Auto-provision `t_user` on first SSO login
6. Configurable session TTL with sliding window (default: no expiry)

---

## Architecture

### Auth type controls login flow only

All three auth types share the same **request-time authentication**: `X-Token` header → `t_user_session` lookup. The `auth.type` config only changes how the user obtains that token (the login flow).

```
auth.type=token  →  POST /login (username+password → t_user check → session token)
auth.type=cas    →  GET /login/sso/redirect → Casdoor → GET /login/sso/callback?ticket=
auth.type=oidc   →  GET /login/sso/redirect → Entra ID → GET /login/sso/callback?code=
```

### Class structure

```
AuthProvider (interface, existing)
  └── TokenAuthProvider (existing, unchanged — active when auth.type=token)

SsoAuthProvider extends AuthProvider (new interface)
AbstractSessionAuthProvider (new abstract class — shared X-Token → t_session logic)
  ├── CasAuthProvider   (new, @ConditionalOnProperty auth.type=cas)
  └── OidcAuthProvider  (new, @ConditionalOnProperty auth.type=oidc)

SsoLoginController    (new — GET /login/sso/redirect + /login/sso/callback)
UserProvisionService  (new — auto-create t_user from SSO attributes)
```

---

## Interface Design

### SsoAuthProvider

```java
public interface SsoAuthProvider extends AuthProvider {
    /** Returns the SSO provider's login URL. callbackUrl is this app's callback endpoint. */
    String getLoginRedirectUrl(String callbackUrl);

    /** Validates the SSO callback (ticket or code) and returns the authenticated SSO user. */
    SsoUser handleCallback(HttpServletRequest request, String callbackUrl);
}

public record SsoUser(String username, String email, String displayName) {}
```

### AbstractSessionAuthProvider

Extracted from `TokenAuthProvider` — validates `X-Token` header against `t_user_session`, enforces session TTL if configured:

```java
abstract class AbstractSessionAuthProvider implements SsoAuthProvider {
    @Override
    public User authenticate(HttpServletRequest request) {
        String token = request.getHeader("X-Token");
        // 1. lookup session by token
        // 2. if ttl-days > 0 and now - lastLoginTime > ttl → delete session, return null
        // 3. if valid and ttl > 0 → update lastLoginTime (sliding window)
        // 4. return user
    }
}
```

---

## CAS Integration (`auth.type=cas`)

Implements standard CAS 2.0 protocol (Apereo spec).

**Login flow:**
1. `GET /login/sso/redirect` → redirect browser to `{cas.server-url}/login?service={encoded callbackUrl}`
2. User authenticates on Casdoor
3. Casdoor redirects to `GET /login/sso/callback?ticket=ST-xxx`
4. Backend calls `{cas.server-url}/serviceValidate?ticket=ST-xxx&service={callbackUrl}&format=json`
5. Parse response → extract `user` attribute (username)
6. `UserProvisionService.provision(username, email)` → get or create `t_user`
7. Create `t_user_session` → return `{ token, workspaceId }`

**Config:**
```yaml
auth:
  type: cas
  cas:
    server-url: https://ams-cas-cn-test.tigerbrokers.net/cas/tiger/flink-platform
    service-url: http://localhost:9104/login/sso/callback
```

**CAS serviceValidate JSON response (format=json):**
```json
{
  "serviceResponse": {
    "authenticationSuccess": {
      "user": "john.doe",
      "attributes": { "email": "john.doe@example.com" }
    }
  }
}
```

---

## OIDC Integration (`auth.type=oidc`)

Implements standard OAuth2 Authorization Code flow + OpenID Connect (RFC 6749, RFC 7519).
Works with any OIDC provider (Entra ID, Casdoor OIDC, Okta, etc.) via discovery.

**Login flow:**
1. `GET /login/sso/redirect` → fetch `{authority}/.well-known/openid-configuration` → build auth URL with `response_type=code&state={random}&scope={scope}`
2. User authenticates on OIDC provider
3. Provider redirects to `GET /login/sso/callback?code=xxx&state=xxx`
4. Backend validates `state`, then POSTs to token endpoint: `code + client_id + client_secret + redirect_uri → access_token + id_token`
5. Parse `id_token` JWT claims → extract `preferred_username` / `email`
6. `UserProvisionService.provision(username, email)` → get or create `t_user`
7. Create `t_user_session` → return `{ token, workspaceId }`

**Config:**
```yaml
auth:
  type: oidc
  oidc:
    authority: https://login.microsoftonline.com/{tenant-id}/v2.0
    client-id: ${OIDC_CLIENT_ID}
    client-secret: ${OIDC_CLIENT_SECRET}
    redirect-uri: http://localhost:9104/login/sso/callback
    scope: openid email profile
```

**Notes:**
- OIDC discovery endpoint `{authority}/.well-known/openid-configuration` is fetched once at startup and cached
- `state` parameter stored in a short-lived in-memory cache (5 min TTL) for CSRF protection
- User info extracted from `id_token` claims: `preferred_username` or `email` as username; Entra ID puts email in `id_token` not `access_token`

---

## Session TTL (Sliding Window)

Applied in `AbstractSessionAuthProvider.authenticate()`:

```yaml
auth:
  session:
    ttl-days: 7    # 0 = never expire (backward-compatible default)
```

- `ttl-days: 0` — no expiry check (existing behavior, default for `token` type)
- `ttl-days: 7` — if `now - lastLoginTime > 7 days` → delete session, return null (force re-login)
- On each valid request where TTL > 0 → update `lastLoginTime` (sliding window: activity extends session)

---

## User Auto-Provisioning

`UserProvisionService.provision(String username, String email)`:

1. Lookup `t_user` by `username`
2. If found → return as-is
3. If not found → create with:
   - `username` from SSO
   - `email` from SSO (if available)
   - `password` = null (SSO users never use password login)
   - `roles` = default configured roles (`auth.sso.default-roles`)
   - `status` = ENABLE

```yaml
auth:
  sso:
    default-roles: '{"global": "VIEWER"}'   # JSON matching UserRoles structure
```

---

## API Endpoints

| Method | Path | Active when | Description |
|--------|------|-------------|-------------|
| GET | `/login/sso/redirect` | `auth.type=cas\|oidc` | Returns SSO login redirect URL |
| GET | `/login/sso/callback` | `auth.type=cas\|oidc` | Handles SSO callback, returns `{token, workspaceId}` |
| POST | `/login` | always | Existing username/password login (unchanged) |
| POST | `/logout` | always | Existing logout (unchanged) |

The frontend calls `GET /login/sso/redirect` to get the redirect URL, then navigates the browser to it. After SSO, the browser lands on `/login/sso/callback` which returns the token as JSON.

---

## Configuration Reference

```yaml
auth:
  type: token           # token | cas | oidc
  session:
    ttl-days: 0         # 0 = never expire; >0 = sliding window in days
  sso:
    default-roles: '{"global":"VIEWER"}'  # default roles for auto-provisioned users
  cas:
    server-url:         # CAS server base URL incl. app path, e.g. https://host/cas/tiger/app-name
    service-url:        # This app's callback URL, e.g. http://localhost:9104/login/sso/callback
  oidc:
    authority:          # OIDC issuer base URL, discovery via /.well-known/openid-configuration
    client-id:          # OAuth2 client ID
    client-secret:      # OAuth2 client secret (use env var: ${OIDC_CLIENT_SECRET})
    redirect-uri:       # This app's callback URL, e.g. http://localhost:9104/login/sso/callback
    scope: openid email profile
```

---

## Files to Create / Modify

### New files
- `flink-platform-web/.../config/auth/SsoAuthProvider.java` — interface
- `flink-platform-web/.../config/auth/SsoUser.java` — record
- `flink-platform-web/.../config/auth/AbstractSessionAuthProvider.java` — shared session validation
- `flink-platform-web/.../config/auth/CasAuthProvider.java`
- `flink-platform-web/.../config/auth/OidcAuthProvider.java`
- `flink-platform-web/.../config/SsoAuthProperties.java` — `@ConfigurationProperties("auth")`
- `flink-platform-web/.../controller/SsoLoginController.java`
- `flink-platform-web/.../service/UserProvisionService.java`

### Modified files
- `TokenAuthProvider.java` — change `@ConditionalOnProperty` to `havingValue = "token"` (remove `matchIfMissing = true`; now explicit)
- `application.yml` — add `auth.session`, `auth.sso`, `auth.cas`, `auth.oidc` config blocks
- `application-dev.yml` — add dev-environment SSO config values

### No schema changes
`t_user` and `t_user_session` tables are unchanged.

---

## Out of Scope

- Frontend login page changes (consuming `/login/sso/redirect` and `/login/sso/callback`)
- Registering the app with Casdoor admin or Entra ID admin (operational task)
- Logout from SSO provider (currently only local session is cleared)
