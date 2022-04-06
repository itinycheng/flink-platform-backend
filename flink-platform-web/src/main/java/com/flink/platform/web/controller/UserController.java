package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.SessionService;
import com.flink.platform.dao.service.UserService;
import com.flink.platform.web.entity.request.UserRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.flink.platform.common.enums.ResponseStatus.ERROR_PARAMETER;
import static com.flink.platform.common.enums.ResponseStatus.USER_HAVE_NO_PERMISSION;
import static com.flink.platform.common.enums.UserType.ADMIN;
import static com.flink.platform.web.entity.response.ResultInfo.failure;

/** user controller. */
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired private UserService userService;

    @Autowired private SessionService sessionService;

    @GetMapping(value = "/get/{userId}")
    public ResultInfo<User> get(@PathVariable Long userId) {
        User user = userService.getById(userId);
        return ResultInfo.success(user);
    }

    @PostMapping(value = "/create")
    public ResultInfo<Long> create(@RequestBody UserRequest userRequest) {
        String errorMsg = userRequest.validateOnCreate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        if (userRequest.getType() == ADMIN) {
            return failure(USER_HAVE_NO_PERMISSION);
        }

        User user = userRequest.getUser();
        user.setId(null);
        userService.save(user);
        return ResultInfo.success(user.getId());
    }

    @PostMapping(value = "/update")
    public ResultInfo<Long> update(@RequestBody UserRequest userRequest) {
        String errorMsg = userRequest.validateOnUpdate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        if (userRequest.getType() == ADMIN) {
            return failure(USER_HAVE_NO_PERMISSION);
        }

        User user = userRequest.getUser();
        userService.updateById(user);
        return ResultInfo.success(user.getId());
    }

    @GetMapping(value = "/page")
    public ResultInfo<IPage<User>> page(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(name = "name", required = false) String name) {
        LambdaQueryWrapper<User> queryWrapper =
                new QueryWrapper<User>()
                        .lambda()
                        .like(Objects.nonNull(name), User::getUsername, name);

        if (loginUser.getType() != ADMIN) {
            queryWrapper.eq(User::getId, loginUser.getId());
        }

        Page<User> pager = new Page<>(page, size);
        IPage<User> iPage = userService.page(pager, queryWrapper);
        return ResultInfo.success(iPage);
    }

    @GetMapping(value = "/info")
    public ResultInfo<Map<String, Object>> info(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser) {

        Map<String, Object> result = new HashMap<>();
        result.put("roles", Arrays.asList("admin", "common"));
        result.put("introduction", "A fixed user given by the backend");
        result.put("avatar", "https://wpimg.wallstcn.com/f778738c-e4f8-4870-b634-56703b4acafe.gif");
        result.put("name", loginUser.getUsername());
        return ResultInfo.success(result);
    }
}
