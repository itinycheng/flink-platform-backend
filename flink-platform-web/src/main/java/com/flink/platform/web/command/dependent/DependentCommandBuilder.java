package com.flink.platform.web.command.dependent;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.task.DependentJob;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.command.CommandBuilder;
import com.flink.platform.web.command.JobCommand;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.quartz.CronExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static com.flink.platform.common.constants.Constant.LINE_SEPARATOR;
import static com.flink.platform.common.enums.JobType.DEPENDENT;
import static java.util.stream.Collectors.joining;

/** condition builder. */
@Slf4j
@Component("dependentCommandBuilder")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DependentCommandBuilder implements CommandBuilder {

    private final JobFlowRunService jobFlowRunService;

    private final JobRunInfoService jobRunInfoService;

    @Override
    public boolean isSupported(JobType jobType, String version) {
        return jobType == DEPENDENT;
    }

    @Override
    public JobCommand buildCommand(@Nonnull JobRunInfo jobRun) {
        var jobRunId = jobRun.getId();
        var flowRunId = jobRun.getFlowRunId();
        var dependentJob = jobRun.getConfig().unwrap(DependentJob.class);
        if (CollectionUtils.isEmpty(dependentJob.getDependentItems())) {
            return new DependentCommand(jobRunId, flowRunId, true, null);
        }

        var dependentItems = dependentJob.getDependentItems();
        var matched = dependentJob.getRelation() == DependentJob.DependentRelation.OR
                ? dependentItems.stream().anyMatch(this::populateAndEvaluateConditions)
                : dependentItems.stream().allMatch(this::populateAndEvaluateConditions);

        var message = dependentItems.stream()
                .map(item -> "flowRunId: %s, jobRunId: %s, status: %s, Verification passed: %s"
                        .formatted(
                                item.getLatestFlowRunId(), item.getLatestJobRunId(), item.getLatestStatus(), matched))
                .collect(joining(LINE_SEPARATOR));

        var dependentCommand = new DependentCommand(jobRunId, flowRunId, matched, message);
        populateTimeout(dependentCommand, jobRun);
        return dependentCommand;
    }

    private boolean populateAndEvaluateConditions(DependentJob.DependentItem dependentItem) {
        if (dependentItem.getFlowId() == null || CollectionUtils.isEmpty(dependentItem.getStatuses())) {
            return true;
        }

        // Get status and create_at of the latest JobRun/JobFlowRun.
        populateLatestExecutionInfo(dependentItem);

        // return if status doesn't match.
        if (!dependentItem.getStatuses().contains(dependentItem.getLatestStatus())) {
            return false;
        }

        var flowRunId = dependentItem.getLatestFlowRunId();
        var createAt = dependentItem.getLatestCreateTime();

        // base time.
        var now = LocalDateTime.now();
        LocalDateTime sinceTime;
        switch (dependentItem.getStrategy()) {
            case LAST_EXECUTION_AFTER_TIME:
                sinceTime = now.minus(dependentItem.parseDuration());
                return !createAt.isBefore(sinceTime);
            case LAST_EXECUTION_AS_EXPECTED:
                // Get next trigger time from sinceTime.
                sinceTime = createAt;
                LocalDateTime nextTriggerTime;
                try {
                    var jobFlowRun = jobFlowRunService.getOne(new QueryWrapper<JobFlowRun>()
                            .lambda()
                            .select(JobFlowRun::getCronExpr)
                            .eq(JobFlowRun::getId, flowRunId));
                    var cronExpression = new CronExpression(jobFlowRun.getCronExpr());
                    var sinceDate =
                            Date.from(sinceTime.atZone(ZoneId.systemDefault()).toInstant());
                    var nextTriggerDate = cronExpression.getNextValidTimeAfter(sinceDate);
                    nextTriggerTime = nextTriggerDate != null
                            ? nextTriggerDate
                                    .toInstant()
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDateTime()
                            : LocalDateTime.MAX;
                } catch (Exception e) {
                    throw new RuntimeException("Get next valid trigger time failed", e);
                }

                return !nextTriggerTime.isBefore(now);
            default:
                throw new RuntimeException("Invalid dependent strategy: " + dependentItem.getStrategy());
        }
    }

    public void populateLatestExecutionInfo(DependentJob.DependentItem dependentItem) {
        if (dependentItem.getJobId() != null) {
            var jobRun = jobRunInfoService.getOne(new QueryWrapper<JobRunInfo>()
                    .lambda()
                    .select(
                            JobRunInfo::getId,
                            JobRunInfo::getFlowRunId,
                            JobRunInfo::getStatus,
                            JobRunInfo::getCreateTime)
                    .eq(JobRunInfo::getJobId, dependentItem.getJobId())
                    .orderByDesc(JobRunInfo::getId)
                    .last("limit 1"));
            if (jobRun != null) {
                dependentItem.setLatestJobRunId(jobRun.getId());
                dependentItem.setLatestFlowRunId(jobRun.getFlowRunId());
                dependentItem.setLatestStatus(jobRun.getStatus());
                dependentItem.setLatestCreateTime(jobRun.getCreateTime());
            }
        } else {
            var jobFlowRun = jobFlowRunService.getOne(new QueryWrapper<JobFlowRun>()
                    .lambda()
                    .select(JobFlowRun::getId, JobFlowRun::getStatus, JobFlowRun::getCreateTime)
                    .eq(JobFlowRun::getFlowId, dependentItem.getFlowId())
                    .orderByDesc(JobFlowRun::getId)
                    .last("limit 1"));
            if (jobFlowRun != null) {
                dependentItem.setLatestFlowRunId(jobFlowRun.getId());
                dependentItem.setLatestStatus(jobFlowRun.getStatus());
                dependentItem.setLatestCreateTime(jobFlowRun.getCreateTime());
            }
        }
    }
}
