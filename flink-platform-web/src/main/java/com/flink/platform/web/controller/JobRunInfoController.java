package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.entity.response.ResultInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Job run info controller. */
@RestController
@RequestMapping("/t-job-run-info")
public class JobRunInfoController {

    @Autowired private JobRunInfoService iJobRunInfoService;

    @GetMapping("{jobId}")
    public ResultInfo<IPage<JobRunInfo>> get(
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "10") Integer size,
            @PathVariable Long jobId) {

        Page<JobRunInfo> pager = new Page<>(page, size);
        IPage<JobRunInfo> iPage =
                this.iJobRunInfoService.page(
                        pager,
                        new QueryWrapper<JobRunInfo>()
                                .lambda()
                                .eq(JobRunInfo::getJobId, jobId)
                                .orderByDesc(JobRunInfo::getId));

        return ResultInfo.success(iPage);
    }
}
