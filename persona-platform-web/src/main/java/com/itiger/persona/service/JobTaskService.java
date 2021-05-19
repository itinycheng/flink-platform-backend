package com.itiger.persona.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.itiger.persona.entity.JobInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * @author tiger
 */
@Slf4j
@Service
@DS("master_platform")
public class JobTaskService {

    @Resource
    public JobInfoQuartzService jobInfoQuartzService;

    @Resource
    public IJobInfoService jobInfoService;

    @Transactional(rollbackFor = Exception.class)
    public boolean saveAndScheduleJob(JobInfo jobInfo) {
        boolean saved = jobInfoService.save(jobInfo);
        if (!saved) {
            throw new RuntimeException("cannot persist job info");
        }
        return jobInfoQuartzService.addJobToQuartz(jobInfo);
    }
}
