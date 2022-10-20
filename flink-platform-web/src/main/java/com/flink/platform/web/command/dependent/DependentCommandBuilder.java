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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

import static com.flink.platform.common.enums.JobType.DEPENDENT;
import static java.util.Objects.nonNull;

/** condition builder. */
@Slf4j
@Component("dependentCommandBuilder")
public class DependentCommandBuilder implements CommandBuilder {

    @Autowired private JobFlowRunService jobFlowRunService;

    @Autowired private JobRunInfoService jobRunInfoService;

    @Override
    public boolean isSupported(JobType jobType, String version) {
        return jobType == DEPENDENT;
    }

    @Override
    public JobCommand buildCommand(Long flowRunId, @Nonnull JobRunInfo jobRunInfo) {
        Long jobRunId = jobRunInfo.getId();
        try {
            DependentJob dependentJob = jobRunInfo.getConfig().unwrap(DependentJob.class);
            if (CollectionUtils.isEmpty(dependentJob.getDependentItems())) {
                return new DependentCommand(jobRunId, true);
            }

            boolean matched =
                    dependentJob.getRelation() == DependentJob.DependentRelation.OR
                            ? dependentJob.getDependentItems().stream()
                                    .anyMatch(this::validateDependentItem)
                            : dependentJob.getDependentItems().stream()
                                    .allMatch(this::validateDependentItem);

            return new DependentCommand(jobRunId, matched);
        } catch (Exception e) {
            log.error("Build dependent command failed, ", e);
            return new DependentCommand(jobRunId, false);
        }
    }

    private boolean validateDependentItem(DependentJob.DependentItem dependentItem) {
        if (dependentItem.getFlowId() == null
                || CollectionUtils.isEmpty(dependentItem.getStatuses())) {
            return true;
        }

        ExecutionStatus latestStatus = null;
        if (dependentItem.getJobId() != null) {
            JobRunInfo jobRunInfo =
                    jobRunInfoService.getOne(
                            new QueryWrapper<JobRunInfo>()
                                    .lambda()
                                    .select(JobRunInfo::getStatus)
                                    .eq(JobRunInfo::getJobId, dependentItem.getJobId())
                                    .ge(
                                            nonNull(dependentItem.getStartTime()),
                                            JobRunInfo::getCreateTime,
                                            dependentItem.getStartTime())
                                    .le(
                                            nonNull(dependentItem.getEndTime()),
                                            JobRunInfo::getCreateTime,
                                            dependentItem.getEndTime())
                                    .orderByDesc(JobRunInfo::getId)
                                    .last("limit 1"));
            if (jobRunInfo != null) {
                latestStatus = jobRunInfo.getStatus();
            }
        } else {
            JobFlowRun jobFlowRun =
                    jobFlowRunService.getOne(
                            new QueryWrapper<JobFlowRun>()
                                    .lambda()
                                    .select(JobFlowRun::getStatus)
                                    .eq(JobFlowRun::getFlowId, dependentItem.getFlowId())
                                    .ge(
                                            nonNull(dependentItem.getStartTime()),
                                            JobFlowRun::getCreateTime,
                                            dependentItem.getStartTime())
                                    .le(
                                            nonNull(dependentItem.getEndTime()),
                                            JobFlowRun::getCreateTime,
                                            dependentItem.getEndTime())
                                    .orderByDesc(JobFlowRun::getId)
                                    .last("limit 1"));
            if (jobFlowRun != null) {
                latestStatus = jobFlowRun.getStatus();
            }
        }

        return dependentItem.getStatuses().contains(latestStatus);
    }
}
