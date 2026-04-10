package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.common.enums.Status;
import com.flink.platform.dao.entity.Config;
import com.flink.platform.dao.service.ConfigService;
import com.flink.platform.web.annotation.RequirePermission;
import com.flink.platform.web.entity.request.ConfigRequest;
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
import java.util.Objects;

import static com.flink.platform.common.enums.Permission.SYSTEM_MANAGE;
import static com.flink.platform.common.enums.ResponseStatus.ERROR_PARAMETER;
import static com.flink.platform.common.enums.Status.DELETED;
import static com.flink.platform.web.entity.response.ResultInfo.failure;
import static com.flink.platform.web.entity.response.ResultInfo.success;

/** Config controller. */
@RestController
@RequestMapping("/config")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ConfigController {

    private final ConfigService configService;

    @RequirePermission(SYSTEM_MANAGE)
    @PostMapping(value = "/create")
    public ResultInfo<Long> create(@RequestBody ConfigRequest configRequest) {
        var errorMsg = configRequest.validateOnCreate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        var config = configRequest.getTConfig();
        config.setId(null);
        configService.save(config);
        return success(config.getId());
    }

    @RequirePermission(SYSTEM_MANAGE)
    @PostMapping(value = "/update")
    public ResultInfo<Long> update(@RequestBody ConfigRequest configRequest) {
        var errorMsg = configRequest.validateOnUpdate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        var config = configRequest.getTConfig();
        configService.updateById(config);
        return success(config.getId());
    }

    @GetMapping(value = "/get/{id}")
    public ResultInfo<Config> get(@PathVariable Long id) {
        var config = configService.getById(id);
        return success(config);
    }

    @RequirePermission(SYSTEM_MANAGE)
    @GetMapping(value = "/delete/{id}")
    public ResultInfo<Boolean> delete(@PathVariable Long id) {
        var config = new Config();
        config.setId(id);
        config.setStatus(DELETED);
        boolean bool = configService.updateById(config);
        return success(bool);
    }

    @RequirePermission(SYSTEM_MANAGE)
    @GetMapping(value = "/purge/{id}")
    public ResultInfo<Boolean> purge(@PathVariable Long id) {
        var bool = configService.removeById(id);
        return success(bool);
    }

    @GetMapping(value = "/page")
    public ResultInfo<IPage<Config>> page(
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "status", required = false) Status status) {
        var queryWrapper = new QueryWrapper<Config>().lambda().like(Objects.nonNull(name), Config::getName, name);
        if (status != null) {
            queryWrapper.eq(Config::getStatus, status);
        } else {
            queryWrapper.ne(Config::getStatus, DELETED);
        }
        var pager = new Page<Config>(page, size);
        var iPage = configService.page(pager, queryWrapper);
        return success(iPage);
    }

    @GetMapping(value = "/list")
    public ResultInfo<List<Config>> list() {
        var list = configService.list();
        return success(list);
    }
}
