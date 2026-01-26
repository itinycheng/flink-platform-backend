package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.enums.JobFlowStatus;
import com.flink.platform.common.enums.JobStatus;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.entity.JobQuartzInfo;
import com.flink.platform.web.entity.request.JobInfoRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.service.QuartzService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static com.flink.platform.common.enums.ExecutionStatus.getNonTerminals;
import static com.flink.platform.common.enums.JobStatus.ONLINE;
import static com.flink.platform.common.enums.ResponseStatus.ERROR_PARAMETER;
import static com.flink.platform.common.enums.ResponseStatus.EXIST_UNFINISHED_PROCESS;
import static com.flink.platform.common.enums.ResponseStatus.NOT_RUNNABLE_STATUS;
import static com.flink.platform.common.enums.ResponseStatus.OPERATION_NOT_ALLOWED;
import static com.flink.platform.common.enums.ResponseStatus.SERVICE_ERROR;
import static com.flink.platform.common.enums.ResponseStatus.USER_HAVE_NO_PERMISSION;
import static com.flink.platform.common.util.DateUtil.GLOBAL_DATE_TIME_FORMAT;
import static com.flink.platform.web.entity.response.ResultInfo.failure;
import static com.flink.platform.web.entity.response.ResultInfo.success;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

/** manage job info. */
@RestController
@RequestMapping("/jobInfo")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class JobInfoController {

    private final JobInfoService jobInfoService;

    private final JobRunInfoService jobRunService;

    private final JobFlowService jobFlowService;

    private final QuartzService quartzService;

    @PostMapping(value = "/create")
    public ResultInfo<JobInfo> create(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestBody JobInfoRequest jobInfoRequest) {
        var errorMsg = jobInfoRequest.validateOnCreate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        var jobInfo = jobInfoRequest.getJobInfo();
        jobInfo.setId(null);
        jobInfo.setStatus(ONLINE);
        jobInfo.setUserId(loginUser.getId());
        jobInfoService.save(jobInfo);
        return success(jobInfo);
    }

    @PostMapping(value = "/update")
    public ResultInfo<JobInfo> update(@RequestBody JobInfoRequest jobInfoRequest) {
        var errorMsg = jobInfoRequest.validateOnUpdate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        var jobInfo = jobInfoRequest.getJobInfo();
        jobInfoService.updateById(jobInfo);
        return success(jobInfo);
    }

    @GetMapping(value = "/get/{jobId}")
    public ResultInfo<JobInfo> get(@PathVariable Long jobId) {
        var jobInfo = jobInfoService.getById(jobId);
        return success(jobInfo);
    }

    @GetMapping(value = "/delete/{jobId}")
    public ResultInfo<Boolean> delete(@PathVariable Long jobId) {
        var bool = jobInfoService.removeById(jobId);
        return success(bool);
    }

    @GetMapping(value = "/page")
    public ResultInfo<IPage<JobInfo>> page(
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(name = "id", required = false) Long id,
            @RequestParam(name = "flowId", required = false) Long flowId,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "status", required = false) JobStatus status,
            @RequestParam(name = "includeJobRuns", required = false, defaultValue = "false") boolean includeJobRuns,
            @RequestParam(name = "excludeJobsInFlow", required = false, defaultValue = "false")
                    boolean excludeJobsInFlow,
            @DateTimeFormat(pattern = GLOBAL_DATE_TIME_FORMAT) @RequestParam(name = "startTime", required = false)
                    LocalDateTime startTime,
            @DateTimeFormat(pattern = GLOBAL_DATE_TIME_FORMAT) @RequestParam(name = "endTime", required = false)
                    LocalDateTime endTime,
            @RequestParam(name = "sort", required = false) String sort) {
        var queryWrapper = new QueryWrapper<JobInfo>()
                .lambda()
                .eq(nonNull(id), JobInfo::getId, id)
                .eq(nonNull(flowId), JobInfo::getFlowId, flowId)
                .like(nonNull(name), JobInfo::getName, name)
                .between(nonNull(startTime) && nonNull(endTime), JobInfo::getCreateTime, startTime, endTime);

        if (status != null) {
            queryWrapper.eq(JobInfo::getStatus, status);
        } else {
            queryWrapper.ne(JobInfo::getStatus, JobFlowStatus.DELETE);
        }

        if ("-id".equals(sort)) {
            queryWrapper.orderByDesc(JobInfo::getId);
        }

        // exclude jobs in workflow dag.
        if (excludeJobsInFlow && flowId != null) {
            List<Long> jobIds = getJobIdsInFlow(flowId);
            queryWrapper.notIn(isNotEmpty(jobIds), JobInfo::getId, jobIds);
        }

        var pager = new Page<JobInfo>(page, size);
        var result = jobInfoService.page(pager, queryWrapper);

        // Add jobRun info.
        if (includeJobRuns && CollectionUtils.isNotEmpty(result.getRecords())) {
            var jobIds = result.getRecords().stream().map(JobInfo::getId).collect(toList());
            var runningJobsMap = jobRunService.listLastWithoutLargeFields(null, jobIds).stream()
                    .collect(toMap(JobRunInfo::getJobId, jobRun -> jobRun));
            result.getRecords().forEach(job -> {
                var jobRun = runningJobsMap.get(job.getId());
                if (jobRun != null) {
                    job.setJobRunId(jobRun.getId());
                    job.setJobRunStatus(jobRun.getStatus());
                    job.setFlowRunId(jobRun.getFlowRunId());
                }
            });
        }

        return success(result);
    }

    @GetMapping(value = "/list")
    public ResultInfo<List<JobInfo>> list(
            @RequestParam(name = "flowId") Long flowId,
            @RequestParam(name = "flag", defaultValue = "all") String flag) {
        List<Long> jobIds = null;
        if ("flow".equals(flag)) {
            jobIds = getJobIdsInFlow(flowId);
        }

        var list = jobInfoService.list(new QueryWrapper<JobInfo>()
                .lambda()
                .select(JobInfo.class, jobInfoService::isNonLargeField)
                .eq(JobInfo::getFlowId, flowId)
                .in(isNotEmpty(jobIds), JobInfo::getId, jobIds));
        return success(list);
    }

    @PostMapping(value = "/getByIds")
    public ResultInfo<List<JobInfo>> getByIds(@RequestBody List<Long> ids) {
        if (isEmpty(ids)) {
            return success(Collections.emptyList());
        }

        var jobs = jobInfoService.list(new QueryWrapper<JobInfo>()
                .lambda()
                .select(JobInfo.class, jobInfoService::isNonLargeField)
                .in(JobInfo::getId, ids));
        return success(jobs);
    }

    @Deprecated
    @GetMapping(value = "/schedule/runOnce/{jobId}")
    public ResultInfo<Long> runOnce(@PathVariable long jobId) {
        var jobInfo = jobInfoService.getById(jobId);
        if (jobInfo.getStatus() != ONLINE) {
            return failure(NOT_RUNNABLE_STATUS);
        }

        var unfinishedJob = jobRunService.getOne(new QueryWrapper<JobRunInfo>()
                .lambda()
                .eq(JobRunInfo::getJobId, jobId)
                .in(JobRunInfo::getStatus, getNonTerminals())
                .last("limit 1"));
        if (unfinishedJob != null) {
            return failure(EXIST_UNFINISHED_PROCESS);
        }

        var quartzInfo = new JobQuartzInfo(jobInfo);
        if (quartzService.runOnce(quartzInfo)) {
            return success(jobId);
        } else {
            return failure(SERVICE_ERROR);
        }
    }

    private List<Long> getJobIdsInFlow(Long flowId) {
        if (flowId == null) {
            return Collections.emptyList();
        }

        var jobFlow = jobFlowService.getById(flowId);
        if (jobFlow != null && jobFlow.getFlow() != null) {
            return jobFlow.getFlow().getVertices().stream()
                    .map(JobVertex::getJobId)
                    .collect(toList());
        } else {
            return Collections.emptyList();
        }
    }

    @GetMapping(value = "/purge/{jobId}")
    public ResultInfo<Long> purge(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser, @PathVariable long jobId) {
        var jobInfo = jobInfoService.getById(jobId);
        if (jobInfo == null) {
            return failure(ERROR_PARAMETER);
        }

        if (!loginUser.getId().equals(jobInfo.getUserId())) {
            return failure(USER_HAVE_NO_PERMISSION);
        }

        var flowId = jobInfo.getFlowId();
        var jobFlow = jobFlowService.getById(flowId);
        if (jobFlow != null && jobFlow.getFlow() != null) {
            if (jobFlow.getFlow().containsVertex(jobId)) {
                return failure(OPERATION_NOT_ALLOWED, "Job is in flow, can't be deleted");
            }
        }

        jobInfoService.deleteAllById(jobId);
        return success(jobId);
    }
}
