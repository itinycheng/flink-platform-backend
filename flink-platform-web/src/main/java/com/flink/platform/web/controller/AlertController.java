package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.dao.entity.AlertInfo;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.AlertService;
import com.flink.platform.web.entity.request.AlertInfoRequest;
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

import static com.flink.platform.common.enums.ResponseStatus.ERROR_PARAMETER;
import static com.flink.platform.web.entity.response.ResultInfo.failure;
import static com.flink.platform.web.entity.response.ResultInfo.success;

/** Alert controller. */
@RestController
@RequestMapping("/alert")
public class AlertController {

    @Autowired
    private AlertService alertService;

    @PostMapping(value = "/create")
    public ResultInfo<Long> create(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestBody AlertInfoRequest alertInfoRequest) {
        String errorMsg = alertInfoRequest.validateOnCreate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        AlertInfo alertInfo = alertInfoRequest.getAlertInfo();
        alertInfo.setId(null);
        alertInfo.setUserId(loginUser.getId());
        alertService.save(alertInfo);
        return success(alertInfo.getId());
    }

    @PostMapping(value = "/update")
    public ResultInfo<Long> update(@RequestBody AlertInfoRequest alertInfoRequest) {
        String errorMsg = alertInfoRequest.validateOnUpdate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        AlertInfo alertInfo = alertInfoRequest.getAlertInfo();
        alertInfo.setUserId(null);
        alertService.updateById(alertInfo);
        return success(alertInfo.getId());
    }

    @GetMapping(value = "/get/{alertId}")
    public ResultInfo<AlertInfo> get(@PathVariable Long alertId) {
        AlertInfo alertInfo = alertService.getById(alertId);
        return success(alertInfo);
    }

    @GetMapping(value = "/delete/{alertId}")
    public ResultInfo<Boolean> delete(@PathVariable Long alertId) {
        boolean bool = alertService.removeById(alertId);
        return success(bool);
    }

    @GetMapping(value = "/page")
    public ResultInfo<IPage<AlertInfo>> page(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(name = "name", required = false) String name) {
        Page<AlertInfo> pager = new Page<>(page, size);
        IPage<AlertInfo> iPage = alertService.page(
                pager,
                new QueryWrapper<AlertInfo>()
                        .lambda()
                        .eq(AlertInfo::getUserId, loginUser.getId())
                        .like(Objects.nonNull(name), AlertInfo::getName, name));

        return success(iPage);
    }

    @GetMapping(value = "/list")
    public ResultInfo<List<AlertInfo>> list(@RequestAttribute(value = Constant.SESSION_USER) User loginUser) {
        List<AlertInfo> list =
                alertService.list(new QueryWrapper<AlertInfo>().lambda().eq(AlertInfo::getUserId, loginUser.getId()));
        return success(list);
    }
}
