package com.flink.platform.web.service;

import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.web.quartz.QuartzService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.flink.platform.common.enums.JobFlowStatus.ONLINE;
import static com.flink.platform.common.enums.JobFlowStatus.SCHEDULING;

/** Schedules job flows in Quartz and resolves their waiting deadlines. */
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class FlowScheduleService {

    private final JobFlowService jobFlowService;

    private final QuartzService quartzService;

    // TODO: handle bool = false.
    @Transactional(rollbackFor = Exception.class)
    public boolean scheduleJob(JobFlow jobFlow) {
        var bool = quartzService.scheduleJobFlow(jobFlow);

        var newJobFlow = new JobFlow();
        newJobFlow.setId(jobFlow.getId());
        newJobFlow.setStatus(SCHEDULING);
        jobFlowService.updateById(newJobFlow);
        return bool;
    }

    @Transactional(rollbackFor = Exception.class)
    public void unscheduleJob(JobFlow jobFlow) {
        quartzService.unscheduleJobFlow(jobFlow);

        var newJobFlow = new JobFlow();
        newJobFlow.setId(jobFlow.getId());
        newJobFlow.setStatus(ONLINE);
        jobFlowService.updateById(newJobFlow);
    }
}
