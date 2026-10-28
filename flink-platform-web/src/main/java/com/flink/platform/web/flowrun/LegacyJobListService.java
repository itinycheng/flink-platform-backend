package com.flink.platform.web.flowrun;

import com.flink.platform.common.model.JobVertex;
import com.flink.platform.dao.entity.ExecutionConfig;
import com.flink.platform.dao.entity.JobFlowDag;
import com.flink.platform.dao.entity.JobFlowDag.NodeLayout;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.dao.service.JobRunInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static org.springframework.transaction.annotation.Isolation.READ_COMMITTED;
import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

/** Creates JOB_LIST runs with their historical start-job admission rule. */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class LegacyJobListService {

    private final JobFlowRunService jobFlowRunService;

    private final JobFlowService jobFlowService;

    private final JobInfoService jobInfoService;

    private final JobRunInfoService jobRunService;

    /**
     * Creates the legacy JOB_LIST run, or returns a blocked result when another start-job run is active.
     *
     * <p>Admission owns its transaction: joining a caller's would hold the flow lock until that caller
     * commits and would let a caller rollback discard the created run. Only this package can reference the
     * class, and {@code REQUIRES_NEW} keeps creation isolated from any same-package caller transaction.
     */
    @Transactional(propagation = REQUIRES_NEW, rollbackFor = Exception.class, isolation = READ_COMMITTED)
    public FlowRunCreationResult attempt(JobFlowRun candidate) {
        var jobFlow = jobFlowService.lockForUpdate(candidate.getFlowId());
        if (jobFlow == null) {
            throw new IllegalStateException("Job flow not found: " + candidate.getFlowId());
        }

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
