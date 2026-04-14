package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.common.enums.Role;
import com.flink.platform.common.enums.Status;
import com.flink.platform.common.model.UserRoles;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.entity.Workspace;
import com.flink.platform.dao.service.UserService;
import com.flink.platform.dao.service.WorkspaceService;
import com.flink.platform.web.annotation.RequirePermission;
import com.flink.platform.web.common.RequestContext;
import com.flink.platform.web.entity.request.WorkspaceRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.flink.platform.common.enums.Permission.SYSTEM_MANAGE;
import static com.flink.platform.common.enums.Permission.WORKSPACE_MANAGE;
import static com.flink.platform.common.enums.Permission.WORKSPACE_VIEW;
import static com.flink.platform.common.enums.ResponseStatus.ERROR_PARAMETER;
import static com.flink.platform.common.enums.Status.DELETED;
import static com.flink.platform.common.enums.Status.ENABLE;
import static com.flink.platform.web.entity.response.ResultInfo.failure;
import static com.flink.platform.web.entity.response.ResultInfo.success;

/** Workspace controller. */
@RestController
@RequestMapping("/workspace")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    private final UserService userService;

    @RequirePermission(SYSTEM_MANAGE)
    @PostMapping(value = "/create")
    public ResultInfo<Workspace> create(@RequestBody WorkspaceRequest request) {
        var errorMsg = request.validateOnCreate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        var workspace = request.getWorkspace();
        workspace.setId(null);
        workspace.setStatus(ENABLE);
        workspaceService.save(workspace);
        return success(workspace);
    }

    @RequirePermission(WORKSPACE_MANAGE)
    @PostMapping(value = "/update")
    public ResultInfo<Workspace> update(@RequestBody WorkspaceRequest request) {
        var errorMsg = request.validateOnUpdate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        var workspace = request.getWorkspace();
        workspaceService.updateById(workspace);
        return success(workspace);
    }

    @RequirePermission(SYSTEM_MANAGE)
    @GetMapping(value = "/delete/{id}")
    public ResultInfo<Boolean> delete(@PathVariable Long id) {
        var workspace = new Workspace();
        workspace.setId(id);
        workspace.setStatus(DELETED);
        return success(workspaceService.updateById(workspace));
    }

    @RequirePermission(WORKSPACE_VIEW)
    @GetMapping(value = "/get/{id}")
    public ResultInfo<Workspace> get(@PathVariable Long id) {
        return success(workspaceService.getById(id));
    }

    @RequirePermission(WORKSPACE_VIEW)
    @GetMapping(value = "/page")
    public ResultInfo<IPage<Workspace>> page(
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "role", required = false) Status status) {
        var queryWrapper =
                new QueryWrapper<Workspace>().lambda().like(StringUtils.isNotEmpty(name), Workspace::getName, name);
        if (status != null) {
            queryWrapper.eq(Workspace::getStatus, status);
        } else {
            queryWrapper.ne(Workspace::getStatus, DELETED);
        }
        var iPage = workspaceService.page(new Page<>(page, size), queryWrapper);
        return success(iPage);
    }

    @RequirePermission(WORKSPACE_MANAGE)
    @PostMapping(value = "/member/update")
    public ResultInfo<Boolean> updateMember(@RequestParam Long userId, @RequestParam(required = false) Role role) {
        var user = userService.getById(userId);
        if (user == null) {
            return failure(ERROR_PARAMETER, "User not found");
        }

        var workspaceId = RequestContext.getWorkspaceId();
        if (workspaceId == null) {
            return failure(ERROR_PARAMETER, "Workspace id not found in request context");
        }

        var newRoles = new UserRoles();
        newRoles.setWorkspaces(Map.of(workspaceId, role));

        var merged = user.getRoles();
        if (merged != null) {
            merged.merge(newRoles);
        } else {
            merged = newRoles;
        }

        var newUser = new User();
        newUser.setId(user.getId());
        newUser.setRoles(merged);
        userService.updateById(newUser);
        return success(true);
    }

    @RequirePermission(WORKSPACE_VIEW)
    @GetMapping(value = "/member/list/{workspaceId}")
    public ResultInfo<List<User>> listMembers(@PathVariable Long workspaceId) {
        var members = userService.list().stream()
                .filter(u ->
                        u.getRoles() != null && u.getRoles().getWorkspaces().containsKey(workspaceId))
                .toList();
        return success(members);
    }
}
