package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.enums.Status;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.entity.Worker;
import com.flink.platform.dao.entity.Workspace;
import com.flink.platform.dao.entity.config.WorkspaceConfig;
import com.flink.platform.dao.service.WorkerService;
import com.flink.platform.dao.service.WorkspaceService;
import com.flink.platform.web.annotation.RequirePermission;
import com.flink.platform.web.annotation.WorkspaceOptional;
import com.flink.platform.web.common.RequestContext;
import com.flink.platform.web.entity.request.WorkspaceRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
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

import java.util.Collections;
import java.util.List;

import static com.flink.platform.common.enums.Permission.SYSTEM_MANAGE;
import static com.flink.platform.common.enums.Permission.WORKSPACE_MANAGE;
import static com.flink.platform.common.enums.Permission.WORKSPACE_VIEW;
import static com.flink.platform.common.enums.ResponseStatus.ERROR_PARAMETER;
import static com.flink.platform.common.enums.Role.SUPER_ADMIN;
import static com.flink.platform.common.enums.Status.DELETED;
import static com.flink.platform.web.entity.response.ResultInfo.failure;
import static com.flink.platform.web.entity.response.ResultInfo.success;

/** Workspace controller. */
@RestController
@RequestMapping("/workspace")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    private final WorkerService workerService;

    @RequirePermission(SYSTEM_MANAGE)
    @PostMapping(value = "/create")
    public ResultInfo<Workspace> create(@RequestBody WorkspaceRequest request) {
        var errorMsg = request.validateOnCreate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        var workspace = request.getWorkspace();
        workspace.setId(null);
        workspace.setStatus(request.getStatus());
        if (workspace.getConfig() == null) {
            workspace.setConfig(new WorkspaceConfig());
        }
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

    @WorkspaceOptional
    @GetMapping(value = "/list")
    public ResultInfo<List<Workspace>> list(@RequestAttribute(value = Constant.SESSION_USER) User loginUser) {
        var roles = loginUser.getRoles();
        List<Workspace> list = List.of();
        if (SUPER_ADMIN.equals(roles.getGlobal())) {
            list = workspaceService.list(new QueryWrapper<Workspace>().lambda().ne(Workspace::getStatus, DELETED));
        } else {
            var workspaces = roles.getWorkspaces();
            if (workspaces != null && !workspaces.isEmpty()) {
                list = workspaceService.list(new QueryWrapper<Workspace>()
                        .lambda()
                        .in(Workspace::getId, workspaces.keySet())
                        .ne(Workspace::getStatus, DELETED));
            }
        }
        return success(list);
    }

    @RequirePermission(WORKSPACE_VIEW)
    @GetMapping(value = "/page")
    public ResultInfo<IPage<Workspace>> page(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "status", required = false) Status status) {
        // no workspace belongs to log in user.
        var userRoles = loginUser.getRoles();
        var workspaces = userRoles.getWorkspaces();
        var isSysManager = userRoles.hasGlobalPermission(SYSTEM_MANAGE);
        if (!isSysManager && workspaces.isEmpty()) {
            return success(new Page<>(page, size));
        }

        var queryWrapper = new QueryWrapper<Workspace>()
                .lambda()
                .like(StringUtils.isNotEmpty(name), Workspace::getName, name)
                .eq(status != null, Workspace::getStatus, status)
                .ne(status == null, Workspace::getStatus, DELETED)
                .in(!isSysManager, Workspace::getId, workspaces.keySet());
        var iPage = workspaceService.page(new Page<>(page, size), queryWrapper);
        return success(iPage);
    }

    @GetMapping(value = "/workers")
    public ResultInfo<List<Worker>> workers() {
        var workspaceId = RequestContext.requireWorkspaceId();
        var workspace = workspaceService.getById(workspaceId);
        var workerIds = workspace.getConfig().getWorkers();
        if (CollectionUtils.isEmpty(workerIds)) {
            return success(Collections.emptyList());
        }

        var list = workerService.list(
                new QueryWrapper<Worker>().lambda().in(Worker::getId, workerIds).ne(Worker::getRole, DELETED));
        return success(list);
    }
}
