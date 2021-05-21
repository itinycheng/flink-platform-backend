package com.itiger.persona.controller;

import com.itiger.persona.common.enums.JobStatusEnum;
import com.itiger.persona.common.enums.ResponseStatus;
import com.itiger.persona.entity.JobInfo;
import com.itiger.persona.entity.response.ResultInfo;
import com.itiger.persona.service.JobInfoQuartzService;
import com.itiger.persona.service.IJobInfoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Objects;

/**
 * all about quartz operations
 *
 * @author tiny.wang
 */
@RestController
@RequestMapping("/jobInfo/quartz")
public class JobQuartzController {

    @Resource
    public JobInfoQuartzService jobInfoQuartzService;

    @Resource
    private IJobInfoService iJobInfoService;

    @GetMapping(value = "/runOnce/{jobId}")
    public ResultInfo runOnce(@PathVariable Long jobId) {
        JobInfo jobInfo = iJobInfoService.getById(jobId);

        // TODO job new -> ready
        if(Objects.nonNull(jobInfo)) {
            jobInfo.setStatus(JobStatusEnum.READY.getCode());
            this.iJobInfoService.updateById(jobInfo);
        }

        if (jobInfoQuartzService.runOnce(jobInfo)) {
            return ResultInfo.success(jobId);
        } else {
            return ResultInfo.failure(ResponseStatus.SERVICE_ERROR);
        }
    }
}