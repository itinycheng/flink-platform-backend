package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.entity.request.JobRunRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static java.util.stream.Collectors.toList;

/** Job run info controller. */
@RestController
@RequestMapping("/jobRun")
public class JobRunController {

    @Autowired private JobRunInfoService jobRunInfoService;

    @Autowired private JobInfoService jobInfoService;

    @GetMapping("{jobId}")
    public ResultInfo<IPage<JobRunInfo>> get(
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "10") Integer size,
            @PathVariable Long jobId) {

        Page<JobRunInfo> pager = new Page<>(page, size);
        IPage<JobRunInfo> iPage =
                jobRunInfoService.page(
                        pager,
                        new QueryWrapper<JobRunInfo>()
                                .lambda()
                                .eq(JobRunInfo::getJobId, jobId)
                                .orderByDesc(JobRunInfo::getId));

        return ResultInfo.success(iPage);
    }

    @GetMapping(value = "/get/{jobId}")
    public ResultInfo<JobRunInfo> get(@PathVariable Long jobId) {
        JobRunInfo jobRunInfo = jobRunInfoService.getById(jobId);
        return ResultInfo.success(jobRunInfo);
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
