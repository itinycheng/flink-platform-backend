package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.model.UserRoles;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.UserService;
import com.flink.platform.web.annotation.RequirePermission;
import com.flink.platform.web.annotation.WorkspaceOptional;
import com.flink.platform.web.entity.request.UserRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import lombok.RequiredArgsConstructor;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.flink.platform.common.enums.Permission.SYSTEM_MANAGE;
import static com.flink.platform.common.enums.Permission.WORKSPACE_MANAGE;
import static com.flink.platform.common.enums.Permission.WORKSPACE_VIEW;
import static com.flink.platform.common.enums.ResponseStatus.ERROR_PARAMETER;
import static com.flink.platform.common.enums.Role.ADMIN;
import static com.flink.platform.common.enums.Role.SUPER_ADMIN;
import static com.flink.platform.web.entity.response.ResultInfo.failure;
import static com.flink.platform.web.entity.response.ResultInfo.success;

/** user controller. */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class UserController {

    private final UserService userService;

    @RequirePermission(SYSTEM_MANAGE)
    @GetMapping(value = "/get/{userId}")
    public ResultInfo<User> get(@PathVariable Long userId) {
        User user = userService.getById(userId);
        return success(user);
    }

    @RequirePermission(SYSTEM_MANAGE)
    @PostMapping(value = "/create")
    public ResultInfo<Long> create(@RequestBody UserRequest userRequest) {
        var errorMsg = userRequest.validateOnCreate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        var user = userRequest.getUser();
        user.setId(null);
        if (user.getRoles() == null) {
            user.setRoles(new UserRoles());
        }
        userService.save(user);
        return success(user.getId());
    }

    @RequirePermission(WORKSPACE_VIEW)
    @PostMapping(value = "/update")
    public ResultInfo<Long> update(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser, @RequestBody UserRequest userRequest) {
        var errorMsg = userRequest.validateOnUpdate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        boolean isSelf = loginUser.getId().equals(userRequest.getId());
        boolean isSuperAdmin = SUPER_ADMIN.equals(loginUser.getRoles().getGlobal());
        if (!isSelf && !isSuperAdmin) {
            return failure(ERROR_PARAMETER, "No permission to update other user's profile");
        }

        // update user profiles except roles.
        var user = userRequest.getUser();
        user.setRoles(null);
        userService.updateById(user);
        return success(user.getId());
    }

    @RequirePermission(WORKSPACE_MANAGE)
    @PostMapping(value = "/update/roles")
    public ResultInfo<Long> updateRoles(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser, @RequestBody UserRequest userRequest) {
        var errorMsg = userRequest.validateOnUpdate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        var user = userService.getById(userRequest.getId());
        errorMsg = checkUserRoles(loginUser, user, userRequest.getRoles());
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        var merged = user.getRoles();
        merged.merge(userRequest.getRoles());
        var newUser = new User();
        newUser.setId(userRequest.getId());
        newUser.setRoles(merged);
        userService.updateById(newUser);
        return success(newUser.getId());
    }

    @RequirePermission(WORKSPACE_MANAGE)
    @GetMapping(value = "/page")
    public ResultInfo<IPage<User>> page(
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(name = "name", required = false) String name) {
        var queryWrapper = new QueryWrapper<User>().lambda().like(Objects.nonNull(name), User::getUsername, name);
        var pager = new Page<User>(page, size);
        var iPage = userService.page(pager, queryWrapper);
        return success(iPage);
    }

    @WorkspaceOptional
    @GetMapping(value = "/info")
    public ResultInfo<Map<String, Object>> info(@RequestAttribute(value = Constant.SESSION_USER) User loginUser) {
        var result = new HashMap<String, Object>();
        result.put("roles", Arrays.asList("admin", "common"));
        result.put("introduction", "A fixed user given by the backend");
        result.put("avatar", "https://wpimg.wallstcn.com/f778738c-e4f8-4870-b634-56703b4acafe.gif");
        result.put("name", loginUser.getUsername());
        return success(result);
    }

    @RequirePermission(WORKSPACE_VIEW)
    @GetMapping(value = "/workspace/{workspaceId}")
    public ResultInfo<List<User>> listMembers(@PathVariable Long workspaceId) {
        var members = userService.list().stream()
                .filter(u -> u.getRoles().getWorkspaces().containsKey(workspaceId))
                .toList();
        return success(members);
    }

    private String checkUserRoles(User loginUser, User user, UserRoles requestRoles) {
        if (requestRoles == null) {
            return "Roles cannot be null";
        }

        // login user is super admin.
        var loginRoles = loginUser.getRoles();
        if (SUPER_ADMIN.equals(loginRoles.getGlobal())) {
            return null;
        }

        // login user isn't super admin, only check workspace roles.
        if (requestRoles.getGlobal() != null) {
            return "Only super admin can assign super admin role";
        }

        var existedWorkspaceRoles = user.getRoles().getWorkspaces();
        var loginWorkspaceRoles = loginRoles.getWorkspaces();
        for (var requestWorkspaceRole : requestRoles.getWorkspaces().entrySet()) {
            var requestWorkspaceId = requestWorkspaceRole.getKey();
            var requestRole = requestWorkspaceRole.getValue();
            var existedRole = existedWorkspaceRoles.get(requestWorkspaceId);
            if (existedRole == requestRole) {
                continue;
            }

            var loginWorkspaceRole = loginWorkspaceRoles.get(requestWorkspaceId);
            if (!ADMIN.equals(loginWorkspaceRole)) {
                return "Only super admin or workspace admin can assign workspace roles";
            }
        }

        return null;
    }
}
