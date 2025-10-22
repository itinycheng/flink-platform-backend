package com.flink.platform.web.command.dependent;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.task.DependentJob;
import com.flink.platform.dao.entity.task.DependentJob.DependentItem;
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
import java.util.List;

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
    public JobCommand buildCommand(Long flowRunId, @Nonnull JobRunInfo jobRun) {
        Long jobRunId = jobRun.getId();
        DependentJob dependentJob = jobRun.getConfig().unwrap(DependentJob.class);
        if (CollectionUtils.isEmpty(dependentJob.getDependentItems())) {
            return new DependentCommand(jobRunId, true, null);
        }

        List<DependentItem> dependentItems = dependentJob.getDependentItems();
        boolean matched = dependentJob.getRelation() == DependentJob.DependentRelation.OR
                ? dependentItems.stream().anyMatch(this::populateAndEvaluateConditions)
                : dependentItems.stream().allMatch(this::populateAndEvaluateConditions);

        String message = dependentItems.stream()
                .map(item -> "flowRunId: %s, jobRunId: %s, status: %s, Verification passed: %s"
                        .formatted(
                                item.getLatestFlowRunId(), item.getLatestJobRunId(), item.getLatestStatus(), matched))
                .collect(joining(LINE_SEPARATOR));

        DependentCommand dependentCommand = new DependentCommand(jobRunId, matched, message);
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

        Long flowRunId = dependentItem.getLatestFlowRunId();
        LocalDateTime createAt = dependentItem.getLatestCreateTime();

        // base time.
        LocalDateTime now = LocalDateTime.now();
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
                    JobFlowRun jobFlowRun = jobFlowRunService.getOne(new QueryWrapper<JobFlowRun>()
                            .lambda()
                            .select(JobFlowRun::getCronExpr)
                            .eq(JobFlowRun::getId, flowRunId));
                    CronExpression cronExpression = new CronExpression(jobFlowRun.getCronExpr());
                    Date sinceDate =
                            Date.from(sinceTime.atZone(ZoneId.systemDefault()).toInstant());
                    Date nextTriggerDate = cronExpression.getNextValidTimeAfter(sinceDate);
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
            JobRunInfo jobRun = jobRunInfoService.getOne(new QueryWrapper<JobRunInfo>()
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
            JobFlowRun jobFlowRun = jobFlowRunService.getOne(new QueryWrapper<JobFlowRun>()
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
