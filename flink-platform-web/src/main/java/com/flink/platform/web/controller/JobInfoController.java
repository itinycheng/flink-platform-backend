package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.common.enums.JobStatus;
import com.flink.platform.common.enums.ResponseStatus;
import com.flink.platform.common.exception.DefinitionException;
import com.flink.platform.common.util.UuidGenerator;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.web.entity.request.JobInfoRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.service.JobQuartzService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/** manage job info. */
@RestController
@RequestMapping("/t-job-info")
public class JobInfoController {

    @Autowired private JobQuartzService jobQuartzService;

    @Autowired private JobInfoService jobInfoService;

    @GetMapping
    public ResultInfo<IPage<JobInfo>> get(
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "10") Integer size,
            JobInfoRequest jobInfoRequest) {

        Page<JobInfo> pager = new Page<>(page, size);
        IPage<JobInfo> iPage =
                this.jobInfoService.page(
                        pager,
                        new QueryWrapper<JobInfo>()
                                .lambda()
                                .eq(
                                        Objects.nonNull(jobInfoRequest.getStatus()),
                                        JobInfo::getStatus,
                                        jobInfoRequest.getStatus()));

        return ResultInfo.success(iPage);
    }

    @GetMapping(value = "{id}")
    public ResultInfo<JobInfo> getOne(@PathVariable String id) {
        JobInfo jobInfo = this.jobInfoService.getById(id);
        return ResultInfo.success(jobInfo);
    }

    @PostMapping(value = "open/{id}")
    public ResultInfo<Boolean> openOne(@PathVariable String id, String cronExpr) {
        JobInfo jobInfo = this.jobInfoService.getById(id);

        boolean result;
        if (Objects.nonNull(jobInfo)) {
            jobInfo.setCronExpr(cronExpr);
            result = this.jobQuartzService.openJob(jobInfo);
        } else {
            throw new DefinitionException(ResponseStatus.ERROR_PARAMETER);
        }

        return ResultInfo.success(result);
    }

    @PostMapping(value = "stop/{id}")
    public ResultInfo<Boolean> stopOne(@PathVariable String id) {
        JobInfo jobInfo = this.jobInfoService.getById(id);

        boolean result;
        if (Objects.nonNull(jobInfo)) {
            result = jobQuartzService.stopJob(jobInfo);
        } else {
            throw new DefinitionException(ResponseStatus.ERROR_PARAMETER);
        }

        return ResultInfo.success(result);
    }

    @PostMapping
    public ResultInfo<Boolean> saveOrUpdate(@RequestBody JobInfoRequest jobInfoRequest) {
        if (StringUtils.isNotBlank(jobInfoRequest.getName())) {
            // save
            if (Objects.isNull(jobInfoRequest.getId())) {
                JobInfo one =
                        this.jobInfoService.getOne(
                                new QueryWrapper<JobInfo>()
                                        .lambda()
                                        .eq(JobInfo::getName, jobInfoRequest.getName()));
                if (Objects.isNull(one)) {
                    // TODO set time status
                    this.buildJobInfo(jobInfoRequest);
                    this.jobInfoService.save(jobInfoRequest);
                    return ResultInfo.success(true);
                } else {
                    throw new DefinitionException(ResponseStatus.ERROR_PARAMETER);
                }
            } else {
                // update
                // TODO update column
                this.jobInfoService.updateById(jobInfoRequest);
                return ResultInfo.success(true);
            }
        } else {
            throw new DefinitionException(ResponseStatus.ERROR_PARAMETER);
        }
    }

    private void buildJobInfo(JobInfoRequest tJobInfoRequest) {
        if (StringUtils.isBlank(tJobInfoRequest.getCode())) {
            tJobInfoRequest.setCode(UuidGenerator.generateShortUuid());
        }

        if (Objects.isNull(tJobInfoRequest.getStatus())) {
            tJobInfoRequest.setStatus(JobStatus.NEW.getCode());
        }

        if (StringUtils.isNotBlank(tJobInfoRequest.getSqlMain())) {
            tJobInfoRequest.setSubject(tJobInfoRequest.getSqlMain());
        }

        tJobInfoRequest.setCatalogs(
                Objects.nonNull(tJobInfoRequest.getCatalogIds())
                        ? tJobInfoRequest.getCatalogIds().toString()
                        : "");
    }
}
