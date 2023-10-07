package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.enums.ResponseStatus;
import com.flink.platform.dao.entity.Session;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.SessionService;
import com.flink.platform.dao.service.UserService;
import com.flink.platform.web.entity.request.UserRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.util.HttpUtil;
import jakarta.servlet.http.HttpServletRequest;
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
import static com.flink.platform.web.entity.response.ResultInfo.failure;
import static com.flink.platform.web.entity.response.ResultInfo.success;

/** user controller. */
@RestController
@RequestMapping
public class LoginController {

    @Autowired
    private UserService userService;

    @Autowired
    private SessionService sessionService;

    @PostMapping(value = "/login")
    public ResultInfo<Map<String, String>> login(@RequestBody User user, HttpServletRequest request) {
        User loginUser = userService.getOne(new QueryWrapper<User>()
                .lambda()
                .eq(User::getUsername, user.getUsername())
                .eq(User::getPassword, user.getPassword()));
        if (loginUser == null) {
            return failure(ResponseStatus.USER_NAME_PASSWD_ERROR);
        }

        String clientIp = HttpUtil.getClientIpAddress(request);
        Session session = sessionService.getOne(new QueryWrapper<Session>()
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

        Map<String, String> result = new HashMap<>(1);
        result.put("token", session.getToken());
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
