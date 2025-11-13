package com.flink.platform.web.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.web.entity.JobFlowQuartzInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.flink.platform.common.enums.JobFlowStatus.ONLINE;
import static com.flink.platform.common.enums.JobFlowStatus.SCHEDULING;

/** Job quartz service. */
@Service
@DS("master_platform")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class JobFlowQuartzService {

    private final JobFlowService jobFlowService;

    private final QuartzService quartzService;

    @Transactional(rollbackFor = Exception.class)
    public boolean scheduleJob(JobFlow jobFlow) {
        var jobFlowQuartzInfo = new JobFlowQuartzInfo(jobFlow);
        var bool = quartzService.addJobToQuartz(jobFlowQuartzInfo);

        var newJobFlow = new JobFlow();
        newJobFlow.setId(jobFlow.getId());
        newJobFlow.setStatus(SCHEDULING);
        jobFlowService.updateById(newJobFlow);

        return bool;
    }

    @Transactional(rollbackFor = Exception.class)
    public void stopJob(JobFlow jobFlow) {
        var quartzInfo = new JobFlowQuartzInfo(jobFlow);
        quartzService.removeJob(quartzInfo);

        var newJobFlow = new JobFlow();
        newJobFlow.setId(jobFlow.getId());
        newJobFlow.setStatus(ONLINE);
        jobFlowService.updateById(newJobFlow);
    }
}
