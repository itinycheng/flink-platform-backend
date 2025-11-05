package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.enums.ResourceType;
import com.flink.platform.dao.entity.Resource;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.ResourceService;
import com.flink.platform.web.entity.request.ResourceRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.service.ResourceManageService;
import com.flink.platform.web.service.StorageService;
import com.flink.platform.web.util.ResourceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.fs.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.flink.platform.common.enums.ResponseStatus.ERROR_PARAMETER;
import static com.flink.platform.common.enums.ResponseStatus.FILE_EXISTS;
import static com.flink.platform.web.entity.response.ResultInfo.failure;
import static com.flink.platform.web.entity.response.ResultInfo.success;

/** Resource controller. */
@Slf4j
@RestController
@RequestMapping("/resource")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ResourceController {

    private final ResourceManageService resourceManageService;

    private final ResourceService resourceService;

    private final StorageService storageService;

    @PostMapping(value = "/create")
    public ResultInfo<Long> create(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestBody ResourceRequest resourceRequest)
            throws Exception {
        var errorMsg = resourceRequest.validateOnCreate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        var resource = resourceRequest.getResource();
        resource.setId(null);
        resource.setUserId(loginUser.getId());
        resourceManageService.save(resource);
        return ResultInfo.success(resource.getId());
    }

    @PostMapping(value = "/update")
    public ResultInfo<Long> update(@RequestBody ResourceRequest resourceRequest) {
        var errorMsg = resourceRequest.validateOnUpdate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        var resource = resourceRequest.getResource();
        resource.setUserId(null);
        resourceService.updateById(resource);
        return ResultInfo.success(resource.getId());
    }

    @GetMapping(value = "/get/{resourceId}")
    public ResultInfo<Resource> get(@PathVariable Long resourceId) {
        var resource = resourceService.getById(resourceId);
        return ResultInfo.success(resource);
    }

    @GetMapping(value = "/getWithParents/{resourceId}")
    public ResultInfo<List<Resource>> getWithParents(@PathVariable Long resourceId) {
        var resource = resourceService.getById(resourceId);
        var parents = recursiveParents(resource.getPid());
        parents.add(resource);
        return ResultInfo.success(parents);
    }

    @GetMapping(value = "/delete/{resourceId}")
    public ResultInfo<Boolean> delete(@PathVariable Long resourceId) throws Exception {
        boolean bool = resourceManageService.removeById(resourceId);
        return ResultInfo.success(bool);
    }

    @GetMapping(value = "/page")
    public ResultInfo<IPage<Resource>> page(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "pid", required = false) Long pid) {
        Page<Resource> pager = new Page<>(page, size);
        LambdaQueryWrapper<Resource> queryWrapper = new QueryWrapper<Resource>()
                .lambda()
                .eq(Resource::getUserId, loginUser.getId())
                .like(Objects.nonNull(name), Resource::getName, name);
        if (pid != null) {
            queryWrapper.eq(Resource::getPid, pid);
        } else {
            queryWrapper.isNull(Resource::getPid);
        }

        IPage<Resource> iPage = resourceService.page(pager, queryWrapper);
        return ResultInfo.success(iPage);
    }

    @GetMapping(value = "/list")
    public ResultInfo<List<Resource>> list(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestParam(name = "type", required = false) ResourceType type) {
        List<Resource> list = resourceService.list(new QueryWrapper<Resource>()
                .lambda()
                .eq(Resource::getUserId, loginUser.getId())
                .eq(Objects.nonNull(type), Resource::getType, type));
        return ResultInfo.success(list);
    }

    @PostMapping("/upload")
    public ResponseEntity<Object> upload(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestParam(name = "id", required = false) Long id,
            @RequestParam(name = "pid") Long pid,
            @RequestParam(name = "file") MultipartFile file)
            throws Exception {
        String localFileName = null;
        try {
            String parentDir = null;
            if (pid != null) {
                var resource = resourceService.getById(pid);
                parentDir = resource.getFullName();
            }
            var absStorageFilePath = resourceManageService.getAbsStorageFilePath(
                    loginUser.getId(), parentDir, file.getOriginalFilename());
            if (id == null && storageService.exists(absStorageFilePath)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(FILE_EXISTS.getDesc());
            }

            localFileName = ResourceUtil.randomLocalTmpFile();
            ResourceUtil.copyToLocal(file, localFileName);
            storageService.copyFromLocal(localFileName, absStorageFilePath, true, true);

            var resource = new Resource();
            resource.setFullName(absStorageFilePath);
            resource.setName(new Path(absStorageFilePath).getName());
            return ResponseEntity.status(HttpStatus.OK).body(success(resource));
        } catch (Exception e) {
            log.error("upload file error", e);
            if (StringUtils.isNotBlank(localFileName)) {
                new File(localFileName).delete();
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/deleteFile")
    public ResultInfo<Boolean> deleteFile(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestBody ResourceRequest resourceRequest)
            throws IOException {
        if (StringUtils.isBlank(resourceRequest.getFullName())) {
            return failure(ERROR_PARAMETER, "file path is null");
        }

        String fullPath = resourceRequest.getFullName();
        if (!storageService.exists(fullPath)) {
            return success(true);
        }

        return success(storageService.trashOrDelete(fullPath, false));
    }

    private List<Resource> recursiveParents(Long pId) {
        if (pId == null || pId < 0) {
            return new ArrayList<>();
        }

        var resource = resourceService.getById(pId);
        var parents = recursiveParents(resource.getPid());
        parents.add(resource);
        return parents;
    }
}
