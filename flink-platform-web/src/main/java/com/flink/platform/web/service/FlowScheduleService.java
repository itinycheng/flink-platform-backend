package com.flink.platform.web.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.model.FlowRunCreationResult;
import com.flink.platform.web.quartz.QuartzService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;

import static com.flink.platform.common.enums.ExecutionStatus.CREATED;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.WAITING;
import static com.flink.platform.common.enums.JobFlowStatus.ONLINE;
import static com.flink.platform.common.enums.JobFlowStatus.SCHEDULING;

/** Schedules job flows in Quartz and admits their flow runs. */
@Service
@DS("master_platform")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class FlowScheduleService {

    private final JobFlowService jobFlowService;

    private final JobFlowRunService jobFlowRunService;

    private final JobFlowRunBizService flowRunBizService;

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

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public FlowRunCreationResult tryCreateFlowRun(JobFlowRun candidate) {
        jobFlowService.lockForUpdate(candidate.getFlowId());

        var timeouts = new ArrayList<FlowRunCreationResult.WaitTimeout>();
        var config = candidate.getConfig();
        var activeRuns = jobFlowRunService.listActiveRuns(candidate.getFlowId(), candidate.getId());
        var expiredRuns = activeRuns.stream()
                .filter(run -> run.getStatus() == WAITING)
                .filter(run -> !resolveWaitDeadline(run).isAfter(LocalDateTime.now()))
                .toList();

        var remainingRuns =
                activeRuns.stream().filter(run -> !expiredRuns.contains(run)).toList();
        var conflict = flowRunBizService.findRunConflict(config, candidate.getScheduleTime(), remainingRuns);
        if (conflict != null) {
            if (candidate.getId() != null) {
                jobFlowRunService.updateStatusFromTo(candidate.getId(), CREATED, FAILURE);
            }
            return FlowRunCreationResult.blocked(
                    conflict.activeRun(), conflict.type().getDescription(), timeouts);
        }

        for (var waitingRun : expiredRuns) {
            // The dispatcher may have advanced or failed this run after the query.
            if (!jobFlowRunService.updateStatusFromTo(waitingRun.getId(), WAITING, FAILURE)) {
                throw new IllegalStateException(
                        "Waiting flow run status changed during admission: " + waitingRun.getId());
            }

            waitingRun.setStatus(FAILURE);
            waitingRun.setEndTime(LocalDateTime.now());
            var deadline = resolveWaitDeadline(waitingRun);
            timeouts.add(new FlowRunCreationResult.WaitTimeout(
                    waitingRun, "Waiting timeout, dependencies not satisfied by " + deadline));
        }

        if (!jobFlowRunService.saveOrUpdate(candidate)) {
            throw new IllegalStateException("Failed to save flow run: " + candidate.getId());
        }

        return FlowRunCreationResult.created(timeouts);
    }
}
