package com.flink.platform.web.command.condition;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.enums.ExecutionCondition;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.common.model.JobEdge;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.dao.entity.JobFlowDag;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.task.ConditionJob;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.command.CommandBuilder;
import com.flink.platform.web.command.JobCommand;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.flink.platform.common.enums.JobType.CONDITION;
import static java.util.stream.Collectors.toList;

/** condition builder. */
@Slf4j
@Component("conditionCommandBuilder")
public class ConditionCommandBuilder implements CommandBuilder {

    @Autowired
    private JobFlowRunService jobFlowRunService;

    @Autowired
    private JobRunInfoService jobRunInfoService;

    @Override
    public boolean isSupported(JobType jobType, String version) {
        return jobType == CONDITION;
    }

    @Override
    public JobCommand buildCommand(Long flowRunId, @Nonnull JobRunInfo jobRunInfo) {
        Long jobRunId = jobRunInfo.getId();
        if (flowRunId == null) {
            return new ConditionCommand(jobRunId, true);
        }

        JobFlowRun jobFlowRun = jobFlowRunService.getById(flowRunId);
        JobFlowDag flow = jobFlowRun.getFlow();
        JobVertex vertex = flow.getVertex(jobRunInfo.getJobId());
        List<Long> jobIds =
                flow.getPreVertices(vertex).stream().map(JobVertex::getJobId).collect(toList());

        if (CollectionUtils.isEmpty(jobIds)) {
            return new ConditionCommand(jobRunId, true);
        }

        List<JobRunInfo> prevJobRunList = jobRunInfoService.list(new QueryWrapper<JobRunInfo>()
                .lambda()
                .eq(JobRunInfo::getFlowRunId, flowRunId)
                .in(JobRunInfo::getJobId, jobIds));

        Long toVertexId = jobRunInfo.getJobId();
        ExecutionCondition condition =
                jobRunInfo.getConfig().unwrap(ConditionJob.class).getCondition();
        boolean success =
                switch (condition) {
                    case AND -> CollectionUtils.isNotEmpty(prevJobRunList)
                            && jobIds.size() == prevJobRunList.size()
                            && prevJobRunList.stream()
                                    .allMatch(jobRun -> jobRun.getStatus()
                                            == getExpectedStatus(flow, jobRun.getJobId(), toVertexId));
                    case OR -> CollectionUtils.isNotEmpty(prevJobRunList)
                            && prevJobRunList.stream()
                                    .anyMatch(jobRun -> jobRun.getStatus()
                                            == getExpectedStatus(flow, jobRun.getJobId(), toVertexId));
                };

        return new ConditionCommand(jobRunId, success);
    }

    private ExecutionStatus getExpectedStatus(JobFlowDag flow, Long fromJobId, Long toJobId) {
        JobEdge edge = flow.getEdge(fromJobId, toJobId);
        if (edge == null) {
            throw new RuntimeException(String.format("No edge found, fromVId: %d, toVid: %d", fromJobId, toJobId));
        }
        return edge.getExpectStatus();
    }
}
