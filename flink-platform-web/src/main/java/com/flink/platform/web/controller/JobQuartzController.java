package com.flink.platform.web.controller;

import com.flink.platform.common.enums.JobStatus;
import com.flink.platform.common.enums.ResponseStatus;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.service.QuartzService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

import java.util.Objects;

/** all about quartz operations. */
@RestController
@RequestMapping("/jobInfo/quartz")
public class JobQuartzController {

    @Resource public QuartzService quartzService;

    @Resource private JobInfoService jobInfoService;

    @GetMapping(value = "/runOnce/{jobId}")
    public ResultInfo<Long> runOnce(@PathVariable Long jobId) {
        JobInfo jobInfo = jobInfoService.getById(jobId);

        if (Objects.nonNull(jobInfo)) {
            jobInfo.setStatus(JobStatus.READY.getCode());
            this.jobInfoService.updateById(jobInfo);
        }

        if (quartzService.runOnce(jobInfo)) {
            return ResultInfo.success(jobId);
        } else {
            return ResultInfo.failure(ResponseStatus.SERVICE_ERROR);
        }
    }

    @GetMapping(value = "/delete")
    public ResultInfo<Object> delete(
            @RequestParam(name = "name") String name, @RequestParam(name = "group") String group) {
        quartzService.deleteTrigger(name, group);
        quartzService.deleteJob(name, group);
        return ResultInfo.success(null);
    }
}
