package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.common.enums.JobStatus;
import com.flink.platform.common.util.UuidGenerator;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.web.entity.request.JobInfoRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.service.JobQuartzService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.flink.platform.common.enums.ResponseStatus.ERROR_PARAMETER;
import static com.flink.platform.web.entity.response.ResultInfo.failure;

/** manage job info. */
@RestController
@RequestMapping("/jobInfo")
public class JobInfoController {

    @Autowired private JobQuartzService jobQuartzService;

    @Autowired private JobInfoService jobInfoService;

    @PostMapping(value = "/create")
    public ResultInfo<JobInfo> create(@RequestBody JobInfoRequest jobInfoRequest) {
        String errorMsg = jobInfoRequest.validateOnCreate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        JobInfo jobInfo = jobInfoRequest.getJobInfo();
        jobInfo.setId(null);
        jobInfo.setCode(UuidGenerator.generateShortUuid());
        jobInfo.setStatus(JobStatus.ONLINE.getCode());
        jobInfoService.save(jobInfo);
        return ResultInfo.success(jobInfo);
    }

    @PostMapping(value = "/update")
    public ResultInfo<JobInfo> update(@RequestBody JobInfoRequest jobInfoRequest) {
        String errorMsg = jobInfoRequest.validateOnUpdate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        JobInfo jobInfo = jobInfoRequest.getJobInfo();
        jobInfo.setCode(null);
        jobInfoService.updateById(jobInfo);
        return ResultInfo.success(jobInfo);
    }

    @GetMapping(value = "/get/{jobId}")
    public ResultInfo<JobInfo> get(@PathVariable Long jobId) {
        JobInfo jobInfo = jobInfoService.getById(jobId);
        return ResultInfo.success(jobInfo);
    }

    @GetMapping(value = "/page")
    public ResultInfo<IPage<JobInfo>> page(
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(name = "name", required = false) String name) {
        Page<JobInfo> pager = new Page<>(page, size);
        IPage<JobInfo> iPage =
                jobInfoService.page(
                        pager,
                        new QueryWrapper<JobInfo>()
                                .lambda()
                                .like(Objects.nonNull(name), JobInfo::getName, name));

        return ResultInfo.success(iPage);
    }

    @PostMapping(value = "/getByIds")
    public ResultInfo<List<JobInfo>> list(@RequestBody List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return ResultInfo.success(Collections.emptyList());
        }

        List<JobInfo> jobs = jobInfoService.listByIds(ids);
        return ResultInfo.success(jobs);
    }

    @Deprecated
    @GetMapping(value = "open/{id}")
    public ResultInfo<Boolean> openOne(@PathVariable String id, String cronExpr) {
        JobInfo jobInfo = this.jobInfoService.getById(id);
        jobInfo.setCronExpr(cronExpr);
        boolean result = jobQuartzService.openJob(jobInfo);
        return ResultInfo.success(result);
    }

    @Deprecated
    @GetMapping(value = "stop/{id}")
    public ResultInfo<Boolean> stop(@PathVariable String id) {
        JobInfo jobInfo = this.jobInfoService.getById(id);
        boolean result = jobQuartzService.stopJob(jobInfo);
        return ResultInfo.success(result);
    }
}
