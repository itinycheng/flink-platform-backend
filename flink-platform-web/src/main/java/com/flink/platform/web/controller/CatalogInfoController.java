package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.enums.CatalogType;
import com.flink.platform.dao.entity.CatalogInfo;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.CatalogInfoService;
import com.flink.platform.web.entity.request.CatalogInfoRequest;
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

import java.util.List;
import java.util.Objects;

import static com.flink.platform.common.constants.Constant.EMPTY;
import static com.flink.platform.common.enums.ResponseStatus.ERROR_PARAMETER;
import static com.flink.platform.web.entity.response.ResultInfo.failure;
import static com.flink.platform.web.entity.response.ResultInfo.success;

/** Catalog info controller. */
@RestController
@RequestMapping("/catalog")
public class CatalogInfoController {

    @Autowired
    private CatalogInfoService catalogService;

    @PostMapping(value = "/create")
    public ResultInfo<Long> create(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestBody CatalogInfoRequest catalogInfoRequest) {
        String errorMsg = catalogInfoRequest.validateOnCreate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        CatalogInfo catalogInfo = catalogInfoRequest.getCatalogInfo();
        catalogInfo.setId(null);
        catalogInfo.setDefaultDatabase(EMPTY);
        catalogInfo.setUserId(loginUser.getId());
        catalogService.save(catalogInfo);
        return success(catalogInfo.getId());
    }

    @PostMapping(value = "/update")
    public ResultInfo<Long> update(@RequestBody CatalogInfoRequest catalogInfoRequest) {
        String errorMsg = catalogInfoRequest.validateOnUpdate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        CatalogInfo catalogInfo = catalogInfoRequest.getCatalogInfo();
        catalogInfo.setUserId(null);
        catalogService.updateById(catalogInfo);
        return success(catalogInfo.getId());
    }

    @GetMapping(value = "/get/{catalogId}")
    public ResultInfo<CatalogInfo> get(@PathVariable Long catalogId) {
        CatalogInfo catalogInfo = catalogService.getById(catalogId);
        return success(catalogInfo);
    }

    @GetMapping(value = "/delete/{catalogId}")
    public ResultInfo<Boolean> delete(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser, @PathVariable Long catalogId) {
        boolean bool = catalogService.remove(new QueryWrapper<CatalogInfo>()
                .lambda()
                .eq(CatalogInfo::getId, catalogId)
                .eq(CatalogInfo::getUserId, loginUser.getId()));
        return success(bool);
    }

    @GetMapping(value = "/page")
    public ResultInfo<IPage<CatalogInfo>> page(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "type", required = false) CatalogType type) {
        Page<CatalogInfo> pager = new Page<>(page, size);
        IPage<CatalogInfo> iPage = catalogService.page(
                pager,
                new QueryWrapper<CatalogInfo>()
                        .lambda()
                        .eq(CatalogInfo::getUserId, loginUser.getId())
                        .eq(Objects.nonNull(type), CatalogInfo::getType, type)
                        .like(Objects.nonNull(name), CatalogInfo::getName, name));
        return success(iPage);
    }

    @GetMapping(value = "/list")
    public ResultInfo<List<CatalogInfo>> list(@RequestAttribute(value = Constant.SESSION_USER) User loginUser) {
        List<CatalogInfo> list = catalogService.list(
                new QueryWrapper<CatalogInfo>().lambda().eq(CatalogInfo::getUserId, loginUser.getId()));
        return success(list);
    }
}
