package com.flink.platform.web.command.condition;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.dao.entity.JobFlowDag;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.command.CommandBuilder;
import com.flink.platform.web.command.JobCommand;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static com.flink.platform.common.enums.JobType.CONDITION;
import static java.util.stream.Collectors.toList;

/** condition builder. */
@Slf4j
@Component("conditionCommandBuilder")
public class ConditionCommandBuilder implements CommandBuilder {

    @Autowired private JobFlowRunService jobFlowRunService;

    @Autowired private JobRunInfoService jobRunInfoService;

    @Override
    public boolean isSupported(JobType jobType, String version) {
        return jobType == CONDITION;
    }

    @Override
    public JobCommand buildCommand(Long flowRunId, JobInfo jobInfo) {
        if (flowRunId == null) {
            return new ConditionCommand(false);
        }

        try {
            JobFlowRun jobFlowRun = jobFlowRunService.getById(flowRunId);
            JobFlowDag flow = jobFlowRun.getFlow();
            JobVertex vertex = flow.getVertex(jobInfo.getId());
            List<Long> jobIds =
                    flow.getPreVertices(vertex).stream().map(JobVertex::getJobId).collect(toList());

            if (CollectionUtils.isEmpty(jobIds)) {
                return new ConditionCommand(true);
            }

            List<JobRunInfo> preJobRunList =
                    jobRunInfoService.list(
                            new QueryWrapper<JobRunInfo>()
                                    .lambda()
                                    .eq(JobRunInfo::getFlowRunId, flowRunId)
                                    .in(JobRunInfo::getJobId, jobIds));

            boolean success =
                    CollectionUtils.isNotEmpty(preJobRunList)
                            && jobIds.size() == preJobRunList.size()
                            && preJobRunList.stream()
                                    .allMatch(jobRun -> jobRun.getStatus() == SUCCESS);
            return new ConditionCommand(success);
        } catch (Exception e) {
            log.error("Build condition command failed, ", e);
            return new ConditionCommand(false);
        }
    }
}
