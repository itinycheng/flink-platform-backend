package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.entity.request.JobRunRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.service.KillJobService;
import org.apache.commons.collections4.CollectionUtils;
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.flink.platform.common.enums.ExecutionStatus.getNonTerminals;
import static com.flink.platform.common.enums.ResponseStatus.NO_RUNNING_JOB_FOUND;
import static com.flink.platform.common.util.DateUtil.GLOBAL_DATE_TIME_FORMAT;
import static com.flink.platform.dao.entity.JobRunInfo.LARGE_FIELDS;
import static com.flink.platform.web.entity.response.ResultInfo.failure;
import static com.flink.platform.web.entity.response.ResultInfo.success;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;

/** Job run info controller. */
@RestController
@RequestMapping("/jobRun")
public class JobRunController {

    @Autowired
    private JobRunInfoService jobRunInfoService;

    @Autowired
    private JobInfoService jobInfoService;

    @Autowired
    private KillJobService killJobService;

    @GetMapping(value = "/get/{runId}")
    public ResultInfo<JobRunInfo> get(@PathVariable Long runId) {
        JobRunInfo jobRunInfo = jobRunInfoService.getById(runId);
        return success(jobRunInfo);
    }

    @GetMapping(value = "/page")
    public ResultInfo<IPage<JobRunInfo>> page(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(name = "id", required = false) Long id,
            @RequestParam(name = "flowRunId", required = false) Long flowRunId,
            @RequestParam(name = "jobId", required = false) Long jobId,
            @RequestParam(name = "status", required = false) ExecutionStatus status,
            @RequestParam(name = "name", required = false) String name,
            @DateTimeFormat(pattern = GLOBAL_DATE_TIME_FORMAT) @RequestParam(name = "startTime", required = false)
                    LocalDateTime startTime,
            @DateTimeFormat(pattern = GLOBAL_DATE_TIME_FORMAT) @RequestParam(name = "endTime", required = false)
                    LocalDateTime endTime,
            @RequestParam(name = "sort", required = false) String sort) {
        LambdaQueryWrapper<JobRunInfo> queryWrapper = new QueryWrapper<JobRunInfo>()
                .lambda()
                .select(JobRunInfo.class, field -> !LARGE_FIELDS.contains(field.getProperty()))
                .eq(JobRunInfo::getUserId, loginUser.getId())
                .eq(nonNull(id), JobRunInfo::getId, id)
                .eq(nonNull(flowRunId), JobRunInfo::getFlowRunId, flowRunId)
                .eq(nonNull(jobId), JobRunInfo::getJobId, jobId)
                .eq(nonNull(status), JobRunInfo::getStatus, status)
                .like(nonNull(name), JobRunInfo::getName, name)
                .between(nonNull(startTime) && nonNull(endTime), JobRunInfo::getCreateTime, startTime, endTime);
        if ("-id".equals(sort)) {
            queryWrapper.orderByDesc(JobRunInfo::getId);
        }

        Page<JobRunInfo> pager = new Page<>(page, size);
        IPage<JobRunInfo> iPage = jobRunInfoService.page(pager, queryWrapper);
        return success(iPage);
    }

    @PostMapping(value = "/getJobOrRunByJobIds")
    public ResultInfo<Collection<Object>> list(@RequestBody JobRunRequest request) {
        if (request.getFlowRunId() == null) {
            return success(Collections.emptyList());
        }

        List<JobRunInfo> jobRunList =
                jobRunInfoService.listLastWithoutLargeFields(request.getFlowRunId(), request.getJobIds());
        List<Long> existedJobs = jobRunList.stream().map(JobRunInfo::getJobId).collect(toList());
        Collection<Long> unRunJobIds = CollectionUtils.subtract(request.getJobIds(), existedJobs);
        List<JobInfo> jobList = jobInfoService.listWithoutLargeFields(unRunJobIds);
        return success(CollectionUtils.union(jobRunList, jobList));
    }

    @GetMapping(value = "/kill/{runId}")
    public ResultInfo<Boolean> kill(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser, @PathVariable Long runId) {
        JobRunInfo jobRun = jobRunInfoService.getOne(new QueryWrapper<JobRunInfo>()
                .lambda()
                .eq(JobRunInfo::getId, runId)
                .eq(JobRunInfo::getUserId, loginUser.getId())
                .in(JobRunInfo::getStatus, getNonTerminals()));

        if (jobRun == null) {
            return failure(NO_RUNNING_JOB_FOUND);
        }

        boolean bool = killJobService.killRemoteJob(jobRun);
        return success(bool);
    }
}
