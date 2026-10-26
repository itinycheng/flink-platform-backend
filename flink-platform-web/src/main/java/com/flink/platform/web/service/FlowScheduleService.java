package com.flink.platform.web.service;

import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.quartz.QuartzService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.flink.platform.common.enums.JobFlowStatus.ONLINE;
import static com.flink.platform.common.enums.JobFlowStatus.SCHEDULING;

/** Schedules job flows in Quartz and resolves their waiting deadlines. */
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class FlowScheduleService {

    private final JobFlowService jobFlowService;

    private final QuartzService quartzService;

    private final WorkerConfig workerConfig;

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

    // TODO: A run created near the next cron fire can receive an impractically short wait deadline.
    public LocalDateTime resolveWaitDeadline(JobFlowRun jobFlowRun) {
        var createTime = jobFlowRun.getCreateTime();
        var cronExpr = jobFlowRun.getCronExpr();
        if (StringUtils.isBlank(cronExpr)) {
            return createTime.plus(workerConfig.getFlowWaitTimeout());
        }

        // A normal run must not wait beyond its next scheduled occurrence.
        var scheduleTime = jobFlowRun.getScheduleTime();
        var scheduledDeadline = quartzService.nextTriggerTimeAfter(cronExpr, scheduleTime);
        if (scheduledDeadline.isPresent() && scheduledDeadline.get().isAfter(createTime)) {
            return scheduledDeadline.get();
        }

        // A historical or severely delayed run waits until the next live scheduled occurrence.
        return quartzService
                .nextTriggerTimeAfter(cronExpr, createTime)
                .orElseGet(() -> createTime.plus(workerConfig.getFlowWaitTimeout()));
    }
}
