package com.flink.platform.web.flowrun;

import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.web.flowrun.FlowRunCreationResult.WaitTimeout;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.WAITING;
import static com.flink.platform.common.enums.JobFlowType.JOB_LIST;
import static com.flink.platform.web.flowrun.FlowRunTransition.Outcome.APPLIED;
import static org.springframework.transaction.annotation.Isolation.READ_COMMITTED;
import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class FlowRunCreationService {

    private final TransactionService transactionService;

    private final LegacyJobListService legacyJobListService;

    @Retryable(maxRetries = 2, delay = 20, jitter = 50)
    public FlowRunCreationResult tryCreateFlowRun(JobFlowRun candidate) {
        // Admission owns its transaction; joining a caller's would hold the flow lock and hide uncommitted runs.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Flow run creation must not run inside a caller transaction");
        }

        // A rolled-back attempt may have populated the entity id.
        candidate.setId(null);
        // TODO: legacy, remove in the future.
        if (JOB_LIST.equals(candidate.getType())) {
            return legacyJobListService.attempt(candidate);
        }

        var result = transactionService.attempt(candidate);
        result.timeouts().forEach(timeout -> {
            timeout.run().setStatus(FAILURE);
            timeout.run().setEndTime(LocalDateTime.now());
        });
        return result;
    }

    // ====================================================
    // ====================== private =====================
    // ====================================================

    /** Owns the admission transaction; package-private so only this package can invoke {@code attempt}. */
    @Service("flowRunTransactionService")
    @RequiredArgsConstructor(onConstructor_ = @Autowired)
    static class TransactionService {

        private final JobFlowService jobFlowService;
        private final JobFlowRunService flowRunService;
        private final FlowRunBizService flowRunBizService;
        private final FlowRunStateService stateService;

        @Transactional(propagation = REQUIRES_NEW, isolation = READ_COMMITTED, rollbackFor = Exception.class)
        public FlowRunCreationResult attempt(JobFlowRun candidate) {
            var jobFlow = jobFlowService.lockForUpdate(candidate.getFlowId());
            if (jobFlow == null) {
                throw new IllegalStateException("Job flow not found: " + candidate.getFlowId());
            }

            var timeouts = new ArrayList<WaitTimeout>();
            var activeRuns = flowRunService.listUnfinishedRuns(candidate.getFlowId());
            var now = LocalDateTime.now();
            var expiredRuns = activeRuns.stream()
                    .filter(run -> run.getStatus() == WAITING)
                    .filter(run -> !flowRunBizService.resolveWaitDeadline(run).isAfter(now))
                    .toList();
            var remainingRuns = activeRuns.stream()
                    .filter(run -> !expiredRuns.contains(run))
                    .toList();
            var conflict = flowRunBizService.findRunConflict(
                    candidate.getConfig(), candidate.getScheduleTime(), remainingRuns);
            if (conflict != null) {
                return rejectConflict(candidate, conflict, timeouts);
            }

            var lostTransition = false;
            for (var waitingRun : expiredRuns) {
                var reason = "Waiting timeout, dependencies not satisfied by "
                        + flowRunBizService.resolveWaitDeadline(waitingRun);
                if (stateService.failWaiting(waitingRun.getId(), reason).outcome() != APPLIED) {
                    lostTransition = true;
                    continue;
                }
                timeouts.add(new FlowRunCreationResult.WaitTimeout(waitingRun, reason));
            }

            if (lostTransition) {
                // One fresh READ_COMMITTED snapshot reconciles every CAS loser; no unbounded transition loop.
                var freshRuns = flowRunService.listUnfinishedRuns(candidate.getFlowId());
                conflict = flowRunBizService.findRunConflict(
                        candidate.getConfig(), candidate.getScheduleTime(), freshRuns);
                if (conflict != null) {
                    return rejectConflict(candidate, conflict, timeouts);
                }
            }

            if (!flowRunService.save(candidate)) {
                throw new IllegalStateException("Failed to save flow run: " + candidate.getId());
            }
            return FlowRunCreationResult.created(candidate, timeouts);
        }

        private FlowRunCreationResult rejectConflict(
                JobFlowRun candidate, FlowRunBizService.RunConflict conflict, List<WaitTimeout> timeouts) {
            return FlowRunCreationResult.blocked(
                    candidate, conflict.activeRun(), conflict.type().getDescription(), timeouts);
        }
    }
}
