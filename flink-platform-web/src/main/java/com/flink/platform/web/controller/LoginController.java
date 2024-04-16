package com.flink.platform.web.controller;

import com.flink.platform.dao.service.SessionService;
import com.flink.platform.dao.service.UserService;
import com.flink.platform.web.entity.request.UserRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.flink.platform.web.entity.response.ResultInfo.success;

/** user controller. */
@RestController
@RequestMapping
public class LoginController {

    @Autowired private UserService userService;

    @Autowired private SessionService sessionService;

    @PostMapping(value = "/login")
    public ResultInfo<Map<String, String>> login(HttpServletRequest request) {
        Map<String, String> result = new HashMap<>(1);
        result.put("token", UUID.randomUUID().toString().replace("-", ""));
        return success(result);
    }

    @PostMapping(value = "/logout")
    public ResultInfo<String> logout(@RequestBody UserRequest userRequest) {
        return success(userRequest.getToken());
    }
}
