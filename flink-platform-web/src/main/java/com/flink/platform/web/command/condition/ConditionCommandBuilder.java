package com.flink.platform.web.command.condition;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.dao.entity.JobFlowDag;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.entity.task.ConditionJob;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.command.CommandBuilder;
import com.flink.platform.web.command.JobCommand;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.flink.platform.common.enums.JobType.CONDITION;
import static java.util.stream.Collectors.toList;

/** condition builder. */
@Slf4j
@Component("conditionCommandBuilder")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ConditionCommandBuilder implements CommandBuilder {

    private final JobFlowRunService jobFlowRunService;

    private final JobRunInfoService jobRunInfoService;

    @Override
    public boolean isSupported(JobType jobType, String version) {
        return jobType == CONDITION;
    }

    @Override
    public JobCommand buildCommand(@Nonnull JobRunInfo jobRunInfo) {
        var jobRunId = jobRunInfo.getId();
        var flowRunId = jobRunInfo.getFlowRunId();
        if (flowRunId == null) {
            return new ConditionCommand(jobRunId, null, true);
        }

        var jobFlowRun = jobFlowRunService.getById(flowRunId);
        var flow = jobFlowRun.getFlow();
        var vertex = flow.getVertex(jobRunInfo.getJobId());
        var jobIds =
                flow.getPreVertices(vertex).stream().map(JobVertex::getJobId).collect(toList());

        if (CollectionUtils.isEmpty(jobIds)) {
            return new ConditionCommand(jobRunId, flowRunId, true);
        }

        var prevJobRunList = jobRunInfoService.list(new QueryWrapper<JobRunInfo>()
                .lambda()
                .eq(JobRunInfo::getFlowRunId, flowRunId)
                .in(JobRunInfo::getJobId, jobIds));

        var toVertexId = jobRunInfo.getJobId();
        var condition = jobRunInfo.getConfig().unwrap(ConditionJob.class).getCondition();
        var success =
                switch (condition) {
                    case AND ->
                        CollectionUtils.isNotEmpty(prevJobRunList)
                                && jobIds.size() == prevJobRunList.size()
                                && prevJobRunList.stream()
                                        .allMatch(jobRun -> jobRun.getStatus()
                                                == getExpectedStatus(flow, jobRun.getJobId(), toVertexId));
                    case OR ->
                        CollectionUtils.isNotEmpty(prevJobRunList)
                                && prevJobRunList.stream()
                                        .anyMatch(jobRun -> jobRun.getStatus()
                                                == getExpectedStatus(flow, jobRun.getJobId(), toVertexId));
                };

        var conditionCommand = new ConditionCommand(jobRunId, flowRunId, success);
        populateTimeout(conditionCommand, jobRunInfo);
        return conditionCommand;
    }

    private ExecutionStatus getExpectedStatus(JobFlowDag flow, Long fromJobId, Long toJobId) {
        var edge = flow.getEdge(fromJobId, toJobId);
        if (edge == null) {
            throw new RuntimeException("No edge found, fromVId: %d, toVid: %d".formatted(fromJobId, toJobId));
        }
        return edge.getExpectStatus();
    }
}
