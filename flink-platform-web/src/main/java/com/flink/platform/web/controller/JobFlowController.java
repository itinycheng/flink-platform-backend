package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobFlowStatus;
import com.flink.platform.common.util.UuidGenerator;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.entity.JobFlowDag;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.web.config.annotation.ApiException;
import com.flink.platform.web.entity.JobFlowQuartzInfo;
import com.flink.platform.web.entity.request.JobFlowRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.service.JobFlowQuartzService;
import com.flink.platform.web.service.QuartzService;
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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.flink.platform.common.enums.ResponseStatus.ERROR_PARAMETER;
import static com.flink.platform.common.enums.ResponseStatus.EXIST_UNFINISHED_PROCESS;
import static com.flink.platform.common.enums.ResponseStatus.NOT_RUNNABLE_STATUS;
import static com.flink.platform.common.enums.ResponseStatus.NO_CRONTAB_SET;
import static com.flink.platform.common.enums.ResponseStatus.SERVICE_ERROR;
import static com.flink.platform.common.enums.ResponseStatus.UNABLE_SCHEDULE_STREAMING_JOB;
import static com.flink.platform.common.enums.ResponseStatus.USER_HAVE_NO_PERMISSION;
import static com.flink.platform.web.entity.response.ResultInfo.failure;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;

/** crud job flow. */
@RestController
@RequestMapping("/jobFlow")
public class JobFlowController {

    @Autowired private JobFlowService jobFlowService;

    @Autowired private JobFlowRunService jobFlowRunService;

    @Autowired private JobFlowQuartzService jobFlowQuartzService;

    @Autowired private QuartzService quartzService;

    @ApiException
    @PostMapping(value = "/create")
    public ResultInfo<Long> create(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestBody JobFlowRequest jobFlowRequest) {
        String errorMsg = jobFlowRequest.validateOnCreate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        JobFlow jobFlow = jobFlowRequest.getJobFlow();
        jobFlow.setId(null);
        jobFlow.setCode(UuidGenerator.generateShortUuid());
        jobFlow.setUserId(loginUser.getId());
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

    @PostMapping(value = "/updateFlow")
    public ResultInfo<Long> updateFlow(@RequestBody JobFlowRequest jobFlowRequest) {
        String errorMsg = jobFlowRequest.validateOnUpdate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        jobFlowService.updateFlowById(jobFlowRequest.getJobFlow());
        return ResultInfo.success(jobFlowRequest.getId());
    }

    @GetMapping(value = "/get/{flowId}")
    public ResultInfo<JobFlow> get(@PathVariable long flowId) {
        JobFlow jobFlow = jobFlowService.getById(flowId);
        return ResultInfo.success(jobFlow);
    }

    @GetMapping(value = "/delete/{flowId}")
    public ResultInfo<Long> delete(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @PathVariable long flowId) {
        JobFlow jobFlow = jobFlowService.getById(flowId);
        if (jobFlow == null) {
            return ResultInfo.failure(ERROR_PARAMETER);
        }

        if (!loginUser.getId().equals(jobFlow.getUserId())) {
            return ResultInfo.failure(USER_HAVE_NO_PERMISSION);
        }

        jobFlowService.deleteAllById(flowId);
        return ResultInfo.success(flowId);
    }

    @GetMapping(value = "/page")
    public ResultInfo<IPage<JobFlow>> page(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "status", required = false) JobFlowStatus status,
            @RequestParam(name = "sort", required = false) String sort) {
        Page<JobFlow> pager = new Page<>(page, size);

        LambdaQueryWrapper<JobFlow> queryWrapper =
                new QueryWrapper<JobFlow>()
                        .lambda()
                        .eq(JobFlow::getUserId, loginUser.getId())
                        .eq(nonNull(status), JobFlow::getStatus, status)
                        .like(nonNull(name), JobFlow::getName, name);
        if ("-id".equals(sort)) {
            queryWrapper.orderByDesc(JobFlow::getId);
        }

        IPage<JobFlow> iPage = jobFlowService.page(pager, queryWrapper);
        return ResultInfo.success(iPage);
    }

    @GetMapping(value = "/idNameMapList")
    public ResultInfo<List<Map<String, Object>>> idNameMap(
            @RequestParam(name = "name", required = false) String name) {
        List<Map<String, Object>> listMap =
                jobFlowService
                        .list(
                                new QueryWrapper<JobFlow>()
                                        .lambda()
                                        .select(JobFlow::getId, JobFlow::getName)
                                        .like(nonNull(name), JobFlow::getName, name))
                        .stream()
                        .map(
                                jobFlow -> {
                                    Map<String, Object> map = new HashMap<>(2);
                                    map.put("id", jobFlow.getId());
                                    map.put("name", jobFlow.getName());
                                    return map;
                                })
                        .collect(toList());
        return ResultInfo.success(listMap);
    }

    @GetMapping(value = "/schedule/start/{flowId}")
    public ResultInfo<Long> start(@PathVariable Long flowId) {
        JobFlowRequest jobFlowRequest = new JobFlowRequest();
        jobFlowRequest.setId(flowId);
        String errorMsg = jobFlowRequest.verifyId();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        JobFlow jobFlow = jobFlowService.getById(jobFlowRequest.getId());
        JobFlowStatus status = jobFlow.getStatus();
        if (status == null || !status.isRunnable()) {
            return failure(NOT_RUNNABLE_STATUS);
        }
        if (StringUtils.isEmpty(jobFlow.getCronExpr())) {
            return failure(NO_CRONTAB_SET);
        }

        JobFlowDag flow = jobFlow.getFlow();
        if (flow == null || jobFlowService.containsStreamingJob(flow)) {
            return failure(UNABLE_SCHEDULE_STREAMING_JOB);
        }

        jobFlowQuartzService.scheduleJob(jobFlow);
        return ResultInfo.success(flowId);
    }

    @GetMapping(value = "/schedule/stop/{flowId}")
    public ResultInfo<Long> stop(@PathVariable Long flowId) {
        JobFlowRequest jobFlowRequest = new JobFlowRequest();
        jobFlowRequest.setId(flowId);
        String errorMsg = jobFlowRequest.verifyId();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        JobFlow jobFlow = jobFlowService.getById(jobFlowRequest.getId());
        if (jobFlow == null) {
            return failure(SERVICE_ERROR, "Job flow not found");
        }

        jobFlowQuartzService.stopJob(jobFlow);
        return ResultInfo.success(flowId);
    }

    @GetMapping(value = "/schedule/runOnce/{flowId}")
    public ResultInfo<Long> runOnce(@PathVariable Long flowId) {
        JobFlow jobFlow = jobFlowService.getById(flowId);
        JobFlowStatus status = jobFlow.getStatus();
        if (status == null || !status.isRunnable()) {
            return failure(NOT_RUNNABLE_STATUS);
        }

        // TODO better in sync lock.
        List<JobFlowRun> notFinishedList =
                jobFlowRunService.list(
                        new QueryWrapper<JobFlowRun>()
                                .lambda()
                                .eq(JobFlowRun::getFlowId, flowId)
                                .in(JobFlowRun::getStatus, ExecutionStatus.getNonTerminals())
                                .gt(JobFlowRun::getCreateTime, LocalDateTime.now().minusDays(1)));
        if (CollectionUtils.isNotEmpty(notFinishedList)) {
            return failure(EXIST_UNFINISHED_PROCESS);
        }

        JobFlowQuartzInfo jobFlowQuartzInfo = new JobFlowQuartzInfo(jobFlow);
        if (quartzService.runOnce(jobFlowQuartzInfo)) {
            return ResultInfo.success(flowId);
        } else {
            return failure(SERVICE_ERROR);
        }
    }
}
