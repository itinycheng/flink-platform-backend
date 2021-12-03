package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.common.enums.JobFlowStatus;
import com.flink.platform.common.util.UuidGenerator;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.web.config.annotation.ApiException;
import com.flink.platform.web.entity.JobFlowQuartzInfo;
import com.flink.platform.web.entity.request.JobFlowRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.service.JobFlowQuartzService;
import com.flink.platform.web.service.QuartzService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

import static com.flink.platform.common.enums.ResponseStatus.ERROR_PARAMETER;
import static com.flink.platform.common.enums.ResponseStatus.NOT_RUNNABLE_STATUS;
import static com.flink.platform.common.enums.ResponseStatus.SERVICE_ERROR;
import static com.flink.platform.web.entity.response.ResultInfo.failure;

/** crud job flow. */
@RestController
@RequestMapping("/jobFlow")
public class JobFlowController {

    @Autowired private JobFlowService jobFlowService;

    @Autowired private JobFlowQuartzService jobFlowQuartzService;

    @Autowired private QuartzService quartzService;

    @ApiException
    @PostMapping(value = "/create")
    public ResultInfo<Long> create(@RequestBody JobFlowRequest jobFlowRequest) {
        String errorMsg = jobFlowRequest.validateOnCreate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        JobFlow jobFlow = jobFlowRequest.getJobFlow();
        jobFlow.setId(null);
        jobFlow.setCode(UuidGenerator.generateShortUuid());
        jobFlow.setStatus(JobFlowStatus.OFFLINE);
        jobFlowService.save(jobFlow);
        return ResultInfo.success(jobFlowRequest.getId());
    }

    @ApiException
    @PostMapping(value = "/update")
    public ResultInfo<Long> update(@RequestBody JobFlowRequest jobFlowRequest) {
        String errorMsg = jobFlowRequest.validateOnUpdate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        jobFlowRequest.setCode(null);
        jobFlowRequest.setUserId(null);
        jobFlowService.updateById(jobFlowRequest.getJobFlow());
        return ResultInfo.success(jobFlowRequest.getId());
    }

    @GetMapping(value = "/get/{flowId}")
    public ResultInfo<JobFlow> getOne(@PathVariable Long flowId) {
        JobFlow jobFlow = jobFlowService.getById(flowId);
        return ResultInfo.success(jobFlow);
    }

    @GetMapping(value = "/list")
    public ResultInfo<IPage<JobFlow>> list(
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(name = "name", required = false) String name) {
        Page<JobFlow> pager = new Page<>(page, size);
        IPage<JobFlow> iPage =
                jobFlowService.page(
                        pager,
                        new QueryWrapper<JobFlow>()
                                .lambda()
                                .like(Objects.nonNull(name), JobFlow::getName, name));

        return ResultInfo.success(iPage);
    }

    @PostMapping(value = "/schedule/start")
    public ResultInfo<Boolean> start(@RequestBody JobFlowRequest jobFlowRequest) {
        String errorMsg = jobFlowRequest.verifyId();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        errorMsg = jobFlowRequest.verifyCronExpr();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        JobFlow jobFlow = jobFlowService.getById(jobFlowRequest.getId());
        JobFlowStatus status = jobFlow.getStatus();
        if (status == null || !status.isRunnable()) {
            return failure(NOT_RUNNABLE_STATUS);
        }

        boolean bool = jobFlowQuartzService.scheduleJob(jobFlowRequest, jobFlow);
        return ResultInfo.success(bool);
    }

    @PostMapping(value = "/schedule/stop")
    public ResultInfo<Boolean> stop(@RequestBody JobFlowRequest jobFlowRequest) {
        String errorMsg = jobFlowRequest.verifyId();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        JobFlow jobFlow = jobFlowService.getById(jobFlowRequest.getId());
        if (jobFlow == null) {
            return failure(SERVICE_ERROR, "Job flow not found");
        }

        boolean bool = jobFlowQuartzService.stopJob(jobFlow);
        return ResultInfo.success(bool);
    }

    @GetMapping(value = "/schedule/runOnce/{flowId}")
    public ResultInfo<Long> runOnce(@PathVariable Long flowId) {
        JobFlow jobFlow = jobFlowService.getById(flowId);
        JobFlowStatus status = jobFlow.getStatus();
        if (status == null || !status.isRunnable()) {
            return failure(NOT_RUNNABLE_STATUS);
        }

        JobFlowQuartzInfo jobFlowQuartzInfo = new JobFlowQuartzInfo(jobFlow);
        if (quartzService.runOnce(jobFlowQuartzInfo)) {
            return ResultInfo.success(flowId);
        } else {
            return failure(SERVICE_ERROR);
        }
    }
}
