package com.flink.platform.web.controller;

import com.flink.platform.common.enums.JobStatusEnum;
import com.flink.platform.common.enums.ResponseStatus;
import com.flink.platform.web.entity.JobInfo;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.service.JobInfoQuartzService;
import com.flink.platform.web.service.IJobInfoService;
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