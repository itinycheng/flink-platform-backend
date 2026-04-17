package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.enums.ResponseStatus;
import com.flink.platform.dao.entity.Session;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.entity.Workspace;
import com.flink.platform.dao.service.SessionService;
import com.flink.platform.dao.service.UserService;
import com.flink.platform.dao.service.WorkspaceService;
import com.flink.platform.web.entity.request.UserRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.util.HttpUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.flink.platform.common.enums.ResponseStatus.USER_NOT_FOUNT;
import static com.flink.platform.common.enums.Role.SUPER_ADMIN;
import static com.flink.platform.common.enums.Status.ENABLE;
import static com.flink.platform.web.entity.response.ResultInfo.failure;
import static com.flink.platform.web.entity.response.ResultInfo.success;

/** user controller. */
@RestController
@RequestMapping
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class LoginController {

    private final UserService userService;

    private final SessionService sessionService;

    private final WorkspaceService workspaceService;

    @PostMapping(value = "/login")
    public ResultInfo<Map<String, Object>> login(@RequestBody UserRequest userRequest, HttpServletRequest request) {
        var loginUser = userService.getOne(new QueryWrapper<User>()
                .lambda()
                .eq(User::getUsername, userRequest.getUsername())
                .eq(User::getPassword, userRequest.getPassword()));
        if (loginUser == null) {
            return failure(ResponseStatus.USER_NAME_PASSWD_ERROR);
        }

        // get or create session.
        var clientIp = HttpUtil.getClientIpAddress(request);
        var session = sessionService.getOne(new QueryWrapper<Session>()
                .lambda()
                .eq(Session::getUserId, loginUser.getId())
                .eq(Session::getIp, clientIp));
        if (session == null) {
            session = new Session();
            session.setToken(UUID.randomUUID().toString().replace("-", ""));
            session.setUserId(loginUser.getId());
            session.setIp(clientIp);
            session.setLastLoginTime(LocalDateTime.now());
            sessionService.save(session);
        }

        // choose one workspace id from user roles.
        var userRoles = loginUser.getRoles();
        Long workspaceId = userRequest.getWorkspaceId();
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

        var result = new HashMap<String, Object>(1);
        result.put("token", session.getToken());
        result.put("workspaceId", workspaceId);
        return success(result);
    }

    @PostMapping(value = "/logout")
    public ResultInfo<String> logout(@RequestBody UserRequest userRequest) {
        if (StringUtils.isEmpty(userRequest.getToken())) {
            return failure(USER_NOT_FOUNT);
        }

        sessionService.remove(new QueryWrapper<Session>().lambda().eq(Session::getToken, userRequest.getToken()));
        return success(userRequest.getToken());
    }
}
