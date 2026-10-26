package com.flink.platform.web.service;

import com.flink.platform.common.model.JobVertex;
import com.flink.platform.dao.entity.ExecutionConfig;
import com.flink.platform.dao.entity.JobFlowDag;
import com.flink.platform.dao.entity.JobFlowDag.NodeLayout;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.model.FlowRunCreationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;

/** Temporary compatibility boundary for JOB_LIST flow runs. */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class LegacyJobListService {

    private final JobFlowRunService jobFlowRunService;

    private final JobInfoService jobInfoService;

    private final JobRunInfoService jobRunService;

    /**
     * Creates the legacy JOB_LIST run, or returns a blocked result when another start-job run is active.
     */
    public FlowRunCreationResult create(JobFlowRun candidate) {
        var executionConfig = candidate.getConfig();
        var activeRun = jobRunService.findActiveRunByJobId(executionConfig.getStartJobId());
        if (activeRun != null) {
            log.warn("The job: {} is in non-terminal status, job run id: {}", activeRun.getName(), activeRun.getId());
            candidate.setStatus(FAILURE);
            return FlowRunCreationResult.blocked(candidate, "There is already an active run: " + activeRun.getId());
        }

        // update flow for jobs of JOB_LIST type.
        candidate.setFlow(createJobListFlow(executionConfig));
        if (!jobFlowRunService.save(candidate)) {
            throw new IllegalStateException("Failed to save JOB_LIST flow run: " + candidate.getId());
        }

        log.info(
                "The job flow of type JOB_LIST: {} is created, time: {}",
                candidate.getId(),
                System.currentTimeMillis());
        return FlowRunCreationResult.created(candidate);
    }

    private JobFlowDag createJobListFlow(ExecutionConfig executionConfig) {
        var flow = new JobFlowDag();
        var startJobId = executionConfig.getStartJobId();
        flow.addVertex(new JobVertex(startJobId));
        // node layouts.
        var jobInfo = jobInfoService.getById(startJobId);
        var classification = jobInfo.getType().getClassification();
        var nodeLayouts = flow.getNodeLayouts();
        nodeLayouts.put(startJobId, new NodeLayout(null, classification, 0, 0));
        return flow;
    }
}
