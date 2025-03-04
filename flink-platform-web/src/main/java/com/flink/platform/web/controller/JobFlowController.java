package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.enums.JobFlowStatus;
import com.flink.platform.common.enums.JobFlowType;
import com.flink.platform.common.util.UuidGenerator;
import com.flink.platform.dao.entity.ExecutionConfig;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.entity.JobFlowDag;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.config.annotation.ApiException;
import com.flink.platform.web.entity.JobFlowQuartzInfo;
import com.flink.platform.web.entity.request.JobFlowRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.service.JobFlowQuartzService;
import com.flink.platform.web.service.QuartzService;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.flink.platform.common.enums.JobFlowStatus.OFFLINE;
import static com.flink.platform.common.enums.JobFlowStatus.ONLINE;
import static com.flink.platform.common.enums.JobFlowStatus.SCHEDULING;
import static com.flink.platform.common.enums.JobFlowType.JOB_FLOW;
import static com.flink.platform.common.enums.JobFlowType.JOB_LIST;
import static com.flink.platform.common.enums.ResponseStatus.ERROR_PARAMETER;
import static com.flink.platform.common.enums.ResponseStatus.EXIST_UNFINISHED_PROCESS;
import static com.flink.platform.common.enums.ResponseStatus.FLOW_ALREADY_SCHEDULED;
import static com.flink.platform.common.enums.ResponseStatus.INVALID_WORKFLOW_TYPE;
import static com.flink.platform.common.enums.ResponseStatus.JOB_LIST_NOT_SUPPORT_SCHEDULING;
import static com.flink.platform.common.enums.ResponseStatus.NOT_RUNNABLE_STATUS;
import static com.flink.platform.common.enums.ResponseStatus.NO_CRONTAB_SET;
import static com.flink.platform.common.enums.ResponseStatus.SERVICE_ERROR;
import static com.flink.platform.common.enums.ResponseStatus.UNABLE_SCHEDULING_JOB_FLOW;
import static com.flink.platform.common.enums.ResponseStatus.USER_HAVE_NO_PERMISSION;
import static com.flink.platform.web.entity.response.ResultInfo.failure;
import static com.flink.platform.web.entity.response.ResultInfo.success;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

/** crud job flow. */
@RestController
@RequestMapping("/jobFlow")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class JobFlowController {

    private final JobFlowService jobFlowService;

    private final JobFlowRunService jobFlowRunService;

    private final JobFlowQuartzService jobFlowQuartzService;

    private final QuartzService quartzService;

    private final JobRunInfoService jobRunService;

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
        jobFlow.setStatus(OFFLINE);
        if (JOB_LIST.equals(jobFlow.getType())) {
            jobFlow.setStatus(ONLINE);
            jobFlow.setFlow(new JobFlowDag());
        }
        jobFlowService.save(jobFlow);
        return success(jobFlowRequest.getId());
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

        if (JOB_LIST.equals(jobFlowRequest.getType())) {
            JobFlow jobFlow = jobFlowService.getById(jobFlowRequest.getId());
            if (OFFLINE.equals(jobFlow.getStatus())) {
                jobFlowRequest.setStatus(ONLINE);
            }
        }

        jobFlowService.updateById(jobFlowRequest.getJobFlow());
        return success(jobFlowRequest.getId());
    }

    @PostMapping(value = "/updateFlow")
    public ResultInfo<Long> updateFlow(@RequestBody JobFlowRequest jobFlowRequest) {
        String errorMsg = jobFlowRequest.validateOnUpdate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        jobFlowService.updateFlowById(jobFlowRequest.getJobFlow());
        return success(jobFlowRequest.getId());
    }

    @GetMapping(value = "/get/{flowId}")
    public ResultInfo<JobFlow> get(@PathVariable long flowId) {
        JobFlow jobFlow = jobFlowService.getById(flowId);
        return success(jobFlow);
    }

    @GetMapping(value = "/copy/{flowId}")
    public ResultInfo<Long> copy(@PathVariable Long flowId) {
        JobFlow jobFlow = jobFlowService.cloneJobFlow(flowId);
        return success(jobFlow.getId());
    }

    @GetMapping(value = "/purge/{flowId}")
    public ResultInfo<Long> purge(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser, @PathVariable long flowId) {
        JobFlow jobFlow = jobFlowService.getById(flowId);
        if (jobFlow == null) {
            return failure(ERROR_PARAMETER);
        }

        if (!loginUser.getId().equals(jobFlow.getUserId())) {
            return failure(USER_HAVE_NO_PERMISSION);
        }

        jobFlowService.deleteAllById(flowId, loginUser.getId());
        return success(flowId);
    }

    @GetMapping(value = "/page")
    public ResultInfo<IPage<JobFlow>> page(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(name = "id", required = false) Long id,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "type", required = false) JobFlowType type,
            @RequestParam(name = "status", required = false) JobFlowStatus status,
            @RequestParam(name = "tag", required = false) String tagCode,
            @RequestParam(name = "sort", required = false) String sort) {
        Page<JobFlow> pager = new Page<>(page, size);

        LambdaQueryWrapper<JobFlow> queryWrapper = new QueryWrapper<JobFlow>()
                .lambda()
                .select(JobFlow.class, field -> !"flow".equals(field.getProperty()))
                .eq(JobFlow::getUserId, loginUser.getId())
                .eq(id != null, JobFlow::getId, id)
                .eq(type != null, JobFlow::getType, type)
                .like(isNotEmpty(name), JobFlow::getName, name)
                .like(isNotEmpty(tagCode), JobFlow::getTags, tagCode);

        if (status != null) {
            queryWrapper.eq(JobFlow::getStatus, status);
        } else {
            queryWrapper.ne(JobFlow::getStatus, JobFlowStatus.DELETE);
        }

        if ("-id".equals(sort)) {
            queryWrapper.orderByDesc(JobFlow::getId);
        }

        IPage<JobFlow> iPage = jobFlowService.page(pager, queryWrapper);
        return success(iPage);
    }

    @GetMapping(value = "/idNameMapList")
    public ResultInfo<List<Map<String, Object>>> idNameMapList(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "status", required = false) List<JobFlowStatus> status) {
        List<Map<String, Object>> listMap = jobFlowService
                .list(new QueryWrapper<JobFlow>()
                        .lambda()
                        .select(JobFlow::getId, JobFlow::getName)
                        .eq(JobFlow::getUserId, loginUser.getId())
                        .like(isNotBlank(name), JobFlow::getName, name)
                        .in(CollectionUtils.isNotEmpty(status), JobFlow::getStatus, status))
                .stream()
                .map(jobFlow -> {
                    Map<String, Object> map = new HashMap<>(2);
                    map.put("id", jobFlow.getId());
                    map.put("name", jobFlow.getName());
                    return map;
                })
                .collect(toList());
        return success(listMap);
    }

    @GetMapping(value = "/schedule/start/{flowId}")
    public ResultInfo<Long> start(@PathVariable Long flowId) {
        JobFlowRequest jobFlowRequest = new JobFlowRequest();
        jobFlowRequest.setId(flowId);
        String errorMsg = jobFlowRequest.verifyId();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        JobFlow jobFlow = jobFlowService.getById(flowId);
        JobFlowStatus status = jobFlow.getStatus();
        if (status == null || !status.isRunnable()) {
            return failure(NOT_RUNNABLE_STATUS);
        }

        if (SCHEDULING.equals(status)) {
            return failure(FLOW_ALREADY_SCHEDULED);
        }

        if (JOB_FLOW.equals(jobFlow.getType())) {
            if (StringUtils.isEmpty(jobFlow.getCronExpr())) {
                return failure(NO_CRONTAB_SET);
            }

            JobFlowDag flow = jobFlow.getFlow();
            if (flow == null || CollectionUtils.isEmpty(flow.getVertices())) {
                return failure(UNABLE_SCHEDULING_JOB_FLOW);
            }
        }

        switch (jobFlow.getType()) {
            case JOB_LIST:
                JobFlow newJobFlow = new JobFlow();
                newJobFlow.setId(jobFlow.getId());
                newJobFlow.setStatus(SCHEDULING);
                jobFlowService.updateById(newJobFlow);
                break;
            case JOB_FLOW:
                jobFlowQuartzService.scheduleJob(jobFlow);
                break;
            default:
                return failure(INVALID_WORKFLOW_TYPE);
        }

        return success(flowId);
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
        return success(flowId);
    }

    @PostMapping(value = "/schedule/runOnce/{flowId}")
    public ResultInfo<Long> runOnce(@PathVariable Long flowId, @RequestBody(required = false) ExecutionConfig config) {
        var jobFlow = jobFlowService.getById(flowId);
        var status = jobFlow.getStatus();
        if (status == null || !status.isRunnable()) {
            return failure(NOT_RUNNABLE_STATUS);
        }

        // TODO: better in sync lock.
        if (JOB_LIST.equals(jobFlow.getType())) {
            // scheduling of job list is unsupported.
            if (config == null || config.getStartJobId() == null) {
                return failure(JOB_LIST_NOT_SUPPORT_SCHEDULING);
            }

            // check if the start job is finished.
            if (jobRunService.findRunningJob(config.getStartJobId()) != null) {
                return failure(EXIST_UNFINISHED_PROCESS);
            }
        } else {
            if (jobFlowRunService.findRunningFlow(flowId, config) != null) {
                return failure(EXIST_UNFINISHED_PROCESS);
            }
        }

        // run once.
        var quartzInfo = new JobFlowQuartzInfo(jobFlow);
        quartzInfo.setConfig(config);
        if (quartzService.runOnce(quartzInfo)) {
            return success(flowId);
        } else {
            return failure(SERVICE_ERROR);
        }
    }
}
