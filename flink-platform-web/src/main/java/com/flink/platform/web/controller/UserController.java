package com.flink.platform.web.controller;

import com.flink.platform.web.entity.response.ResultInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** user test. */
@RestController
@RequestMapping("/user")
public class UserController {

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
    public ResultInfo<Map<String, String>> login(@RequestBody Map<String, String> userInfo) {
        Map<String, String> result = new HashMap<>();
        result.put("token", "temp_token");
        return ResultInfo.success(result);
    }

    @PostMapping(value = "/logout")
    public ResultInfo<String> logout(String token) {
        return ResultInfo.success(token);
    }
}
