package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.entity.request.JobRunRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import org.apache.commons.collections4.CollectionUtils;
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;

/** Job run info controller. */
@RestController
@RequestMapping("/jobRun")
public class JobRunController {

    @Autowired private JobRunInfoService jobRunInfoService;

    @Autowired private JobInfoService jobInfoService;

    @GetMapping(value = "/get/{runId}")
    public ResultInfo<JobRunInfo> get(@PathVariable Long runId) {
        JobRunInfo jobRunInfo = jobRunInfoService.getById(runId);
        return ResultInfo.success(jobRunInfo);
    }

    @GetMapping(value = "/page")
    public ResultInfo<IPage<JobRunInfo>> page(
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(name = "flowRunId", required = false) Long flowRunId,
            @RequestParam(name = "jobId", required = false) Long jobId,
            @RequestParam(name = "status", required = false) ExecutionStatus status,
            @RequestParam(name = "name", required = false) String name,
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
                    @RequestParam(name = "startTime", required = false)
                    LocalDateTime startTime,
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
                    @RequestParam(name = "endTime", required = false)
                    LocalDateTime endTime,
            @RequestParam(name = "sort", required = false) String sort) {
        LambdaQueryWrapper<JobRunInfo> queryWrapper =
                new QueryWrapper<JobRunInfo>()
                        .lambda()
                        .eq(nonNull(flowRunId), JobRunInfo::getFlowRunId, flowRunId)
                        .eq(nonNull(jobId), JobRunInfo::getJobId, jobId)
                        .eq(nonNull(status), JobRunInfo::getStatus, status)
                        .like(nonNull(name), JobRunInfo::getName, name)
                        .between(
                                nonNull(startTime) && nonNull(endTime),
                                JobRunInfo::getCreateTime,
                                startTime,
                                endTime);
        if ("-id".equals(sort)) {
            queryWrapper.orderByDesc(JobRunInfo::getId);
        }

        Page<JobRunInfo> pager = new Page<>(page, size);
        IPage<JobRunInfo> iPage = jobRunInfoService.page(pager, queryWrapper);
        return ResultInfo.success(iPage);
    }

    @PostMapping(value = "/getJobOrRunByJobIds")
    public ResultInfo<Collection<Object>> list(@RequestBody JobRunRequest jobRunRequest) {
        if (CollectionUtils.isEmpty(jobRunRequest.getJobIds())
                || jobRunRequest.getFlowRunId() == null) {
            return ResultInfo.success(Collections.emptyList());
        }

        List<JobRunInfo> jobRunList =
                jobRunInfoService.list(
                        new QueryWrapper<JobRunInfo>()
                                .lambda()
                                .eq(JobRunInfo::getFlowRunId, jobRunRequest.getFlowRunId())
                                .in(JobRunInfo::getJobId, jobRunRequest.getJobIds()));

        List<Long> existedJobs =
                CollectionUtils.emptyIfNull(jobRunList).stream()
                        .map(JobRunInfo::getJobId)
                        .collect(toList());

        Collection<Long> unRunJobIds =
                CollectionUtils.subtract(jobRunRequest.getJobIds(), existedJobs);

        List<JobInfo> jobList = null;
        if (CollectionUtils.isNotEmpty(unRunJobIds)) {
            jobList = jobInfoService.listByIds(unRunJobIds);
        }
        Collection<Object> jobAndJobRunList =
                CollectionUtils.union(
                        CollectionUtils.emptyIfNull(jobRunList),
                        CollectionUtils.emptyIfNull(jobList));
        return ResultInfo.success(jobAndJobRunList);
    }
}
