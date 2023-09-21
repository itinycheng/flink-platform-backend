package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.enums.Status;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobParam;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.JobInfoService;
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

import static com.flink.platform.common.constants.JobConstant.PARAM_FORMAT;
import static com.flink.platform.common.enums.JobParamType.GLOBAL;
import static com.flink.platform.common.enums.JobParamType.JOB_FLOW;
import static com.flink.platform.common.enums.ResponseStatus.ERROR_PARAMETER;
import static com.flink.platform.common.enums.ResponseStatus.OPERATION_NOT_ALLOWED;
import static com.flink.platform.web.entity.response.ResultInfo.failure;
import static com.flink.platform.web.entity.response.ResultInfo.success;
import static java.lang.String.format;
import static java.util.Objects.nonNull;

/** Alert controller. */
@RestController
@RequestMapping("/jobParam")
public class JobParamController {

    @Autowired
    private JobParamService jobParamService;

    @Autowired
    private JobInfoService jobService;

    @PostMapping(value = "/create")
    public ResultInfo<Long> create(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestBody JobParamRequest jobParamRequest) {
        String errorMsg = jobParamRequest.validateOnCreate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        JobParam existed = jobParamService.getOne(new QueryWrapper<JobParam>()
                .lambda()
                .eq(JobParam::getParamName, jobParamRequest.getParamName())
                .eq(JobParam::getType, jobParamRequest.getType())
                .eq(JOB_FLOW.equals(jobParamRequest.getType()), JobParam::getFlowId, jobParamRequest.getFlowId())
                .eq(JobParam::getUserId, loginUser.getId())
                .last("limit 1"));
        if (existed != null) {
            return failure(ERROR_PARAMETER, "param name already exists");
        }

        JobParam jobParam = jobParamRequest.getJobParam();
        jobParam.setId(null);
        jobParam.setUserId(loginUser.getId());
        jobParam.setStatus(Status.ENABLE);
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
        JobParam jobParam = jobParamService.getById(paramId);

        // JobParamType.JOB_FLOW unhandled.
        if (GLOBAL == jobParam.getType()) {
            JobInfo jobInfo = jobService.getOne(new QueryWrapper<JobInfo>()
                    .lambda()
                    .eq(JobInfo::getUserId, jobParam.getUserId())
                    .like(JobInfo::getSubject, format(PARAM_FORMAT, jobParam.getParamName()))
                    .last("LIMIT 1"));
            if (jobInfo != null) {
                return failure(
                        OPERATION_NOT_ALLOWED,
                        format("The param is being used in job: %s, cannot be removed", jobInfo.getName()));
            }
        }

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
        IPage<JobParam> iPage = jobParamService.page(
                pager,
                new QueryWrapper<JobParam>()
                        .lambda()
                        .eq(JobParam::getUserId, loginUser.getId())
                        .like(nonNull(name), JobParam::getParamName, name));

        return success(iPage);
    }

    @GetMapping(value = "/list")
    public ResultInfo<List<JobParam>> list(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestParam(name = "flowId", required = false) Long flowId,
            @RequestParam(name = "status", required = false) Status status) {
        List<JobParam> list = jobParamService.list(new QueryWrapper<JobParam>()
                .lambda()
                .eq(JobParam::getUserId, loginUser.getId())
                .eq(nonNull(flowId), JobParam::getFlowId, flowId)
                .eq(nonNull(status), JobParam::getStatus, status));
        return success(list);
    }
}
