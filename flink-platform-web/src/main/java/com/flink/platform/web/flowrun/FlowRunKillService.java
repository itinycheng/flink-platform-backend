package com.flink.platform.web.flowrun;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.service.KillJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import static com.flink.platform.common.enums.ExecutionStatus.KILLED;
import static com.flink.platform.common.enums.ExecutionStatus.KILLING;

/**
 * handle killing and killed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowRunKillService {

    private final FlowRunStateService stateService;

    private final JobFlowRunService flowRunService;

    private final JobRunInfoService jobRunService;

    private final KillJobService jobKillService;

    public FlowRunTransition forceKilled(Long id) {
        return stateService.forceKilled(id);
    }

    public FlowRunTransition requestKill(Long id) {
        var transition = stateService.requestKill(id);
        cleanupAfterKilling(id, transition.status());
        return transition;
    }

    public FlowRunTransition requestKill(Long id, String workerHost) {
        var transition = stateService.requestKill(id, workerHost);
        cleanupAfterKilling(id, transition.status());
        return transition;
    }

    public boolean advanceKilling(Long id) {
        var run = flowRunService.getLiteById(id);
        if (run == null || run.getStatus() == KILLED) {
            return true;
        }

        if (run.getStatus() == KILLING) {
            cleanupAfterKilling(id, run.getStatus());
            return confirmAfterJobRunKilled(id);
        }

        return false;
    }

    private boolean confirmAfterJobRunKilled(Long id) {
        try {
            var jobRunList = jobRunService.listLiteNonTerminalRuns(id);
            if (CollectionUtils.isEmpty(jobRunList)) {
                return stateService.confirmKilled(id).status() == KILLED;
            }
        } catch (Exception e) {
            log.error("Failed to confirm flow-run kill from child states, flowRunId={}", id, e);
        }

        return false;
    }

    private void cleanupAfterKilling(Long id, @Nullable ExecutionStatus status) {
        if (status != KILLING) {
            return;
        }

        try {
            var runList = jobRunService.listLiteNonTerminalRuns(id);
            if (CollectionUtils.isEmpty(runList)) {
                stateService.forceKilled(id);
                return;
            }

            if (!runList.parallelStream()
                    .map(jobKillService::attemptToKillJob)
                    .reduce((bool1, bool2) -> bool1 && bool2)
                    .orElse(false)) {
                log.warn("One or more child kill requests failed, flowRunId={}", id);
            }
        } catch (Exception e) {
            log.error("Failed to clean up child job runs, flowRunId={}", id, e);
        }
    }
}
