package com.flink.platform.web.command.dependent;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.task.DependentJob;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.command.CommandBuilder;
import com.flink.platform.web.command.JobCommand;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.quartz.CronExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static com.flink.platform.common.enums.JobType.DEPENDENT;

/** condition builder. */
@Slf4j
@Component("dependentCommandBuilder")
public class DependentCommandBuilder implements CommandBuilder {

    @Autowired
    private JobFlowRunService jobFlowRunService;

    @Autowired
    private JobRunInfoService jobRunInfoService;

    @Override
    public boolean isSupported(JobType jobType, String version) {
        return jobType == DEPENDENT;
    }

    @Override
    public JobCommand buildCommand(Long flowRunId, @Nonnull JobRunInfo jobRunInfo) {
        Long jobRunId = jobRunInfo.getId();
        DependentJob dependentJob = jobRunInfo.getConfig().unwrap(DependentJob.class);
        if (CollectionUtils.isEmpty(dependentJob.getDependentItems())) {
            return new DependentCommand(jobRunId, true);
        }

        boolean matched = dependentJob.getRelation() == DependentJob.DependentRelation.OR
                ? dependentJob.getDependentItems().stream().anyMatch(this::validateDependentItem)
                : dependentJob.getDependentItems().stream().allMatch(this::validateDependentItem);

        return new DependentCommand(jobRunId, matched);
    }

    private boolean validateDependentItem(DependentJob.DependentItem dependentItem) {
        if (dependentItem.getFlowId() == null || CollectionUtils.isEmpty(dependentItem.getStatuses())) {
            return true;
        }

        // Get status and create_at of the latest JobRun/JobFlowRun.
        Triple<Long, ExecutionStatus, LocalDateTime> latestExecution = getLatestExecution(dependentItem);
        Long flowRunId = latestExecution.getLeft();
        ExecutionStatus status = latestExecution.getMiddle();
        LocalDateTime createAt = latestExecution.getRight();

        // return if status doesn't match.
        if (!dependentItem.getStatuses().contains(status)) {
            return false;
        }

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

    public Triple<Long, ExecutionStatus, LocalDateTime> getLatestExecution(DependentJob.DependentItem dependentItem) {
        if (dependentItem.getJobId() != null) {
            JobRunInfo jobRunInfo = jobRunInfoService.getOne(new QueryWrapper<JobRunInfo>()
                    .lambda()
                    .select(JobRunInfo::getFlowRunId, JobRunInfo::getStatus, JobRunInfo::getCreateTime)
                    .eq(JobRunInfo::getJobId, dependentItem.getJobId())
                    .orderByDesc(JobRunInfo::getId)
                    .last("limit 1"));
            if (jobRunInfo != null) {
                return Triple.of(jobRunInfo.getFlowRunId(), jobRunInfo.getStatus(), jobRunInfo.getCreateTime());
            }
        } else {
            JobFlowRun jobFlowRun = jobFlowRunService.getOne(new QueryWrapper<JobFlowRun>()
                    .lambda()
                    .select(JobFlowRun::getId, JobFlowRun::getStatus, JobFlowRun::getCreateTime)
                    .eq(JobFlowRun::getFlowId, dependentItem.getFlowId())
                    .orderByDesc(JobFlowRun::getId)
                    .last("limit 1"));
            if (jobFlowRun != null) {
                return Triple.of(jobFlowRun.getId(), jobFlowRun.getStatus(), jobFlowRun.getCreateTime());
            }
        }

        throw new RuntimeException("Get latest flowRunId/status/createTime failed");
    }
}
