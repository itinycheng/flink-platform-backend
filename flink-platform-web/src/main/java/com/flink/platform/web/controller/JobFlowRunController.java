package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.common.annotation.Auditable;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.OperationType;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.web.annotation.RequirePermission;
import com.flink.platform.web.dto.ResultInfo;
import com.flink.platform.web.dto.request.JobFlowRunRequest;
import com.flink.platform.web.flowrun.FlowRunKillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

import static com.flink.platform.common.enums.EntityType.FLOW_RUN;
import static com.flink.platform.common.enums.ExecutionStatus.KILLED;
import static com.flink.platform.common.enums.ExecutionStatus.KILLING;
import static com.flink.platform.common.enums.Permission.TASK_EDIT;
import static com.flink.platform.common.enums.Permission.TASK_EXEC;
import static com.flink.platform.common.enums.Permission.TASK_VIEW;
import static com.flink.platform.common.enums.ResponseStatus.ERROR_PARAMETER;
import static com.flink.platform.common.enums.ResponseStatus.FLOW_ALREADY_TERMINATED;
import static com.flink.platform.common.enums.ResponseStatus.KILL_FLOW_EXCEPTION_FOUND;
import static com.flink.platform.common.util.DateUtil.GLOBAL_DATE_TIME_FORMAT;
import static com.flink.platform.web.dto.ResultInfo.failure;
import static com.flink.platform.web.dto.ResultInfo.success;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;

/** crud job flow. */
@Slf4j
@RestController
@RequestMapping("/jobFlowRun")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class JobFlowRunController {

    private final JobFlowRunService jobFlowRunService;

    private final FlowRunKillService killService;

    @RequirePermission(TASK_VIEW)
    @GetMapping(value = "/get/{flowRunId}")
    public ResultInfo<JobFlowRun> get(@PathVariable long flowRunId) {
        var jobFlowRun = jobFlowRunService.getById(flowRunId);
        return success(jobFlowRun);
    }

    @RequirePermission(TASK_EDIT)
    @Auditable(type = FLOW_RUN, operation = OperationType.UPDATE)
    @PostMapping(value = "/update")
    public ResultInfo<Long> update(@RequestBody JobFlowRunRequest request) {
        var errorMsg = request.validateOnUpdate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        // TODO: Audit legacy KILLED updates as KILL; this self-call bypasses forceKilled's audit aspect.
        if (request.getStatus() == KILLED) {
            return forceKilled(request.getId());
        }

        request.setFlow(null);
        request.setFlowId(null);
        request.setUserId(null);
        request.setCronExpr(null);
        jobFlowRunService.updateById(request.getJobFlowRun());
        return success(request.getId());
    }

    @RequirePermission(TASK_VIEW)
    @GetMapping(value = "/page")
    public ResultInfo<IPage<JobFlowRun>> page(
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(name = "id", required = false) Long id,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "status", required = false) ExecutionStatus status,
            @RequestParam(name = "tag", required = false) String tagCode,
            @DateTimeFormat(pattern = GLOBAL_DATE_TIME_FORMAT) @RequestParam(name = "startTime", required = false)
                    LocalDateTime startTime,
            @DateTimeFormat(pattern = GLOBAL_DATE_TIME_FORMAT) @RequestParam(name = "endTime", required = false)
                    LocalDateTime endTime,
            @RequestParam(name = "sort", required = false) String sort) {
        var queryWrapper = new QueryWrapper<JobFlowRun>()
                .lambda()
                .select(JobFlowRun.class, field -> !"flow".equals(field.getProperty()))
                .eq(nonNull(id), JobFlowRun::getId, id)
                .eq(nonNull(status), JobFlowRun::getStatus, status)
                .likeRight(isNotEmpty(name), JobFlowRun::getName, name)
                .like(isNotEmpty(tagCode), JobFlowRun::getTags, tagCode)
                .nested(nonNull(startTime) && nonNull(endTime), qw -> qw.isNull(JobFlowRun::getEndTime)
                        .or()
                        .between(JobFlowRun::getEndTime, startTime, endTime));
        if ("-id".equals(sort)) {
            queryWrapper.orderByDesc(JobFlowRun::getId);
        }

        var pager = new Page<JobFlowRun>(page, size);
        var iPage = jobFlowRunService.page(pager, queryWrapper);
        return success(iPage);
    }

    @RequirePermission(TASK_EXEC)
    @Auditable(type = FLOW_RUN, operation = OperationType.KILL, auditOnFailure = true)
    @GetMapping(value = "/kill/{flowRunId}")
    public ResultInfo<Long> kill(@PathVariable Long flowRunId) {
        var transition = killService.requestKill(flowRunId);
        var status = transition.status();
        if (status == KILLING || status == KILLED) {
            return success(flowRunId);
        }

        return status != null && status.isTerminalState()
                ? failure(FLOW_ALREADY_TERMINATED)
                : failure(KILL_FLOW_EXCEPTION_FOUND);
    }

    @RequirePermission(TASK_EXEC)
    @Auditable(type = FLOW_RUN, operation = OperationType.KILL, auditOnFailure = true)
    @PostMapping(value = "/force-killed/{flowRunId}")
    public ResultInfo<Long> forceKilled(@PathVariable Long flowRunId) {
        if (flowRunId == null || flowRunId <= 0) {
            return failure(ERROR_PARAMETER);
        }

        var transition = killService.forceKilled(flowRunId);
        var status = transition.status();
        if (status == KILLED) {
            return success(flowRunId);
        }

        return status != null && status.isTerminalState()
                ? failure(FLOW_ALREADY_TERMINATED)
                : failure(KILL_FLOW_EXCEPTION_FOUND);
    }
}
