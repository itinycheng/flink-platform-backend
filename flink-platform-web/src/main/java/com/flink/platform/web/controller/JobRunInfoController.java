package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.web.entity.JobRunInfo;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.service.IJobRunInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * @Author Shik
 * @Title: JobRunInfoController
 * @ProjectName: flink-platform-backend
 * @Description: TODO
 * @Date: 2021/5/24 下午2:20
 */
@RestController
@RequestMapping("/t-job-run-info")
public class JobRunInfoController {

    @Autowired
    private IJobRunInfoService iJobRunInfoService;

    @GetMapping("{jobId}")
    public ResultInfo get(@RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
                          @RequestParam(name = "size", required = false, defaultValue = "10") Integer size,
                          @PathVariable Long jobId,
                          HttpServletRequest request) {

        Page pager = new Page<>(page, size);
        IPage iPage = this.iJobRunInfoService.page(pager, new QueryWrapper<JobRunInfo>().lambda()
                .eq(JobRunInfo::getJobId, jobId).orderByDesc(JobRunInfo::getId));

        return ResultInfo.success(iPage);
    }

}
