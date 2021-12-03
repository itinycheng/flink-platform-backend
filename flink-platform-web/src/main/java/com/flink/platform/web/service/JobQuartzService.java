package com.flink.platform.web.service;

import com.flink.platform.common.enums.JobStatus;
import com.flink.platform.common.enums.ResponseStatus;
import com.flink.platform.common.exception.DefinitionException;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.web.entity.JobQuartzInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Job quartz service. */
@Service
public class JobQuartzService {

    @Autowired private JobInfoService jobInfoService;

    @Autowired private QuartzService quartzService;

    @Transactional(rollbackFor = RuntimeException.class)
    public boolean stopJob(JobInfo jobInfo) {
        jobInfo.setStatus(JobStatus.OFFLINE.getCode());
        boolean update = jobInfoService.updateById(jobInfo);
        if (!update) {
            throw new DefinitionException(ResponseStatus.SERVICE_ERROR);
        }
        // 移除job quartz
        JobQuartzInfo jobQuartzInfo = new JobQuartzInfo(jobInfo);
        this.quartzService.removeJob(jobQuartzInfo);
        return true;
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public boolean openJob(JobInfo jobInfo) {
        jobInfo.setStatus(JobStatus.ONLINE.getCode());
        jobInfoService.updateById(jobInfo);

        jobInfo = jobInfoService.getById(jobInfo.getId());
        JobQuartzInfo jobQuartzInfo = new JobQuartzInfo(jobInfo);
        quartzService.removeJob(jobQuartzInfo);
        return quartzService.addJobToQuartz(jobQuartzInfo);
    }
}
