package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.dao.entity.JobParam;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.JobParamService;
import com.flink.platform.web.entity.request.JobParamRequest;
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
@RequestMapping("/jobParam")
public class JobParamController {

    @Autowired private JobParamService jobParamService;

    @PostMapping(value = "/create")
    public ResultInfo<Long> create(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestBody JobParamRequest jobParamRequest) {
        String errorMsg = jobParamRequest.validateOnCreate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        JobParam jobParam = jobParamRequest.getJobParam();
        jobParam.setId(null);
        jobParam.setUserId(loginUser.getId());
        jobParamService.save(jobParam);
        return success(jobParam.getId());
    }

    @PostMapping(value = "/update")
    public ResultInfo<Long> update(@RequestBody JobParamRequest jobParamRequest) {
        String errorMsg = jobParamRequest.validateOnUpdate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        JobParam jobParam = jobParamRequest.getJobParam();
        jobParam.setUserId(null);
        jobParamService.updateById(jobParam);
        return success(jobParam.getId());
    }

    @GetMapping(value = "/get/{paramId}")
    public ResultInfo<JobParam> get(@PathVariable Long paramId) {
        JobParam jobParam = jobParamService.getById(paramId);
        return success(jobParam);
    }

    @GetMapping(value = "/delete/{paramId}")
    public ResultInfo<Boolean> delete(@PathVariable Long paramId) {
        boolean bool = jobParamService.removeById(paramId);
        return success(bool);
    }

    @GetMapping(value = "/page")
    public ResultInfo<IPage<JobParam>> page(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(name = "name", required = false) String name) {
        Page<JobParam> pager = new Page<>(page, size);
        IPage<JobParam> iPage =
                jobParamService.page(
                        pager,
                        new QueryWrapper<JobParam>()
                                .lambda()
                                .eq(JobParam::getUserId, loginUser.getId())
                                .like(Objects.nonNull(name), JobParam::getParamName, name));

        return success(iPage);
    }

    @GetMapping(value = "/list")
    public ResultInfo<List<JobParam>> list(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser) {
        List<JobParam> list =
                jobParamService.list(
                        new QueryWrapper<JobParam>()
                                .lambda()
                                .eq(JobParam::getUserId, loginUser.getId()));
        return success(list);
    }
}
