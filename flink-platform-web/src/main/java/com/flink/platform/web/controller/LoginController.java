package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.enums.ResponseStatus;
import com.flink.platform.dao.entity.Session;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.entity.Workspace;
import com.flink.platform.dao.service.SessionService;
import com.flink.platform.dao.service.UserService;
import com.flink.platform.dao.service.WorkspaceService;
import com.flink.platform.web.config.AuthProperties;
import com.flink.platform.web.config.auth.AuthProvider;
import com.flink.platform.web.entity.request.LoginRequest;
import com.flink.platform.web.entity.request.UserRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.service.UserProvisionService;
import com.flink.platform.web.util.HttpUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.flink.platform.common.enums.Role.SUPER_ADMIN;
import static com.flink.platform.common.enums.Status.ENABLE;
import static com.flink.platform.web.entity.response.ResultInfo.failure;
import static com.flink.platform.web.entity.response.ResultInfo.success;

/** Login / logout endpoints. Supports token (password), CAS and OIDC auth modes. */
@Slf4j
@RestController
@RequestMapping
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class LoginController {

    private final UserService userService;

    private final SessionService sessionService;

    private final WorkspaceService workspaceService;

    private final AuthProperties props;

    private final UserProvisionService userProvisionService;

    private final AuthProvider authProvider;

    @GetMapping("/login/config")
    public ResultInfo<Map<String, Object>> loginConfig() {
        var result = new HashMap<String, Object>(2);
        result.put("authType", props.getType());
        result.put("ssoLoginUrl", authProvider.getLoginRedirectUrl());
        return success(result);
    }

    @PostMapping("/login")
    public ResultInfo<Map<String, Object>> login(@RequestBody LoginRequest req, HttpServletRequest request) {
        var clientIp = HttpUtil.getClientIpAddress(request);
        return switch (props.getType()) {
            case LOCAL -> passwordLogin(req, clientIp);
            case CAS, OIDC -> ssoLogin(req, clientIp);
        };
    }

    @PostMapping(value = "/logout")
    public ResultInfo<Map<String, Object>> logout(@RequestBody UserRequest userRequest) throws IOException {
        if (StringUtils.isNotEmpty(userRequest.getToken())) {
            sessionService.remove(new QueryWrapper<Session>().lambda().eq(Session::getToken, userRequest.getToken()));
        }

        var result = new HashMap<String, Object>();
        result.put("redirectUrl", authProvider.getLogoutRedirectUrl());
        return success(result);
    }

    // ============================================================
    // ===================== Private helpers ======================
    // ============================================================

    private ResultInfo<Map<String, Object>> ssoLogin(LoginRequest req, String clientIp) {
        var ticketOrCode = req.getTicket() != null ? req.getTicket() : req.getCode();
        var ssoUser = authProvider.handleCallback(ticketOrCode, req.getState());
        var userId = userProvisionService.provision(ssoUser);
        var user = userService.getById(userId);
        var session = getOrCreateSession(user.getId(), clientIp);
        log.info("SSO login successful: user={}, ip={}", user.getUsername(), clientIp);
        return buildLoginResponse(session.getToken(), resolveWorkspaceId(user, req));
    }

    private ResultInfo<Map<String, Object>> passwordLogin(LoginRequest req, String clientIp) {
        var loginUser = userService.getOne(new QueryWrapper<User>()
                .lambda()
                .eq(User::getUsername, req.getUsername())
                .eq(User::getPassword, req.getPassword()));
        if (loginUser == null) {
            return failure(ResponseStatus.USER_NAME_PASSWD_ERROR);
        }

        var session = getOrCreateSession(loginUser.getId(), clientIp);
        return buildLoginResponse(session.getToken(), resolveWorkspaceId(loginUser, req));
    }

    private Long resolveWorkspaceId(User loginUser, LoginRequest request) {
        // choose one workspace id from user roles.
        var userRoles = loginUser.getRoles();
        Long workspaceId = request.getWorkspaceId();
        if (workspaceId == null || !userRoles.hasWorkspaceRole(workspaceId)) {
            workspaceId = userRoles.getAnyWorkspaceId();
        }

        // choose any workspace id for super admin.
        if (workspaceId == null && SUPER_ADMIN.equals(userRoles.getGlobal())) {
            workspaceId = workspaceService
                    .getOne(new QueryWrapper<Workspace>()
                            .lambda()
                            .eq(Workspace::getStatus, ENABLE)
                            .last("limit 1"))
                    .getId();
        }
        return workspaceId;
    }

    private Session getOrCreateSession(long userId, String ip) {
        var existing = sessionService.getOne(new QueryWrapper<Session>()
                .lambda()
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

    private static ResultInfo<Map<String, Object>> buildLoginResponse(String token, @Nullable Long workspaceId) {
        var result = new HashMap<String, Object>(2);
        result.put("token", token);
        result.put("workspaceId", workspaceId);
        return success(result);
    }
}
