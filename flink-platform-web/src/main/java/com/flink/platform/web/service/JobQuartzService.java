package com.flink.platform.web.service;

import com.flink.platform.common.enums.JobStatus;
import com.flink.platform.common.enums.ResponseStatus;
import com.flink.platform.common.exception.DefinitionException;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.service.JobInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Job quartz service. */
@Service
public class JobQuartzService {

    @Autowired private JobInfoService jobInfoService;

    @Autowired private QuartzService quartzService;

    @Transactional(rollbackFor = RuntimeException.class)
    public boolean stopJob(JobInfo jobInfo) {
        jobInfo.setStatus(JobStatus.STOPPED.getCode()).setUpdateTime(LocalDateTime.now());
        boolean update = jobInfoService.updateById(jobInfo);
        if (!update) {
            throw new DefinitionException(ResponseStatus.SERVICE_ERROR);
        }
        // 移除job quartz
        this.quartzService.removeJob(jobInfo.getCode());
        return true;
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public boolean openJob(JobInfo jobInfo) {
        jobInfo.setStatus(JobStatus.SCHEDULED.getCode()).setUpdateTime(LocalDateTime.now());
        boolean update = jobInfoService.updateById(jobInfo);
        if (!update) {
            throw new DefinitionException(ResponseStatus.SERVICE_ERROR);
        }
        quartzService.removeJob(jobInfo.getCode());
        return quartzService.addJobToQuartz(jobInfo);
    }
}
