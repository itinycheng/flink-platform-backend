package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.enums.ResponseStatus;
import com.flink.platform.dao.entity.Session;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.SessionService;
import com.flink.platform.dao.service.UserService;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.util.HttpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** user controller. */
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired private UserService userService;

    @Autowired private SessionService sessionService;

    @GetMapping(value = "/info")
    public ResultInfo<Map<String, Object>> info(String token) {
        Map<String, Object> result = new HashMap<>();
        result.put("roles", Arrays.asList("admin", "common"));
        result.put("introduction", "A fixed user given by the backend");
        result.put("avatar", "https://wpimg.wallstcn.com/f778738c-e4f8-4870-b634-56703b4acafe.gif");
        result.put("name", "Fixed User");
        return ResultInfo.success(result);
    }

    @PostMapping(value = "/login")
    public ResultInfo<Map<String, String>> login(
            @RequestBody User user, HttpServletRequest request) {
        User loginUser =
                userService.getOne(
                        new QueryWrapper<User>()
                                .lambda()
                                .eq(User::getUsername, user.getUsername())
                                .eq(User::getPassword, user.getPassword()));
        if (loginUser == null) {
            return ResultInfo.failure(ResponseStatus.USER_NAME_PASSWD_ERROR);
        }

        String clientIp = HttpUtil.getClientIpAddress(request);
        Session session =
                sessionService.getOne(
                        new QueryWrapper<Session>()
                                .lambda()
                                .eq(Session::getUserId, loginUser.getId())
                                .eq(Session::getIp, clientIp));

        if (session == null) {
            session = new Session();
            session.setId(UUID.randomUUID().toString().replace("-", ""));
            session.setUserId(loginUser.getId());
            session.setIp(clientIp);
            session.setLastLoginTime(LocalDateTime.now());
            sessionService.save(session);
        }

        Map<String, String> result = new HashMap<>(1);
        result.put("token", session.getId());
        return ResultInfo.success(result);
    }

    @PostMapping(value = "/logout")
    public ResultInfo<String> logout(String token) {
        return ResultInfo.success(token);
    }
}
