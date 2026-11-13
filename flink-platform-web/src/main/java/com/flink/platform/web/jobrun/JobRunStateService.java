package com.flink.platform.web.jobrun;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobRunInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.flink.platform.common.enums.ExecutionStatus.CREATED;
import static com.flink.platform.common.enums.ExecutionStatus.ERROR;
import static com.flink.platform.common.enums.ExecutionStatus.KILLED;
import static com.flink.platform.common.enums.ExecutionStatus.KILLING;
import static com.flink.platform.common.enums.ExecutionStatus.RUNNING;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static com.flink.platform.common.enums.ExecutionStatus.UNFINISHED_STATUSES;
import static com.flink.platform.common.enums.ExecutionStatus.WAITING;

/**
 * Conditional job-run status writes: decisions follow a fixed edge, observations are fenced by the
 * reported status.
 */
@Service
@RequiredArgsConstructor
public class JobRunStateService {

    private static final List<ExecutionStatus> OBSERVED_STATUSES = List.of(SUBMITTED, RUNNING);

    private final JobRunInfoService jobRunService;

    // ==================== Decisions ====================

    /** CREATED -> SUBMITTED before the external command starts. */
    public JobRunTransition submitCreated(Long id, JobRunInfo update) {
        return transition(id, TransitionSpec.SUBMIT_CREATED, update);
    }

    /** CREATED or SUBMITTED -> ERROR when the flow side fails. */
    public JobRunTransition completeWithError(Long id, JobRunInfo update) {
        return transition(id, TransitionSpec.COMPLETE_WITH_ERROR, update);
    }

    /** Claims the run for a kill; rejects if another caller changed the row first. */
    public JobRunTransition requestKill(Long id) {
        return transition(id, TransitionSpec.REQUEST_KILL, new JobRunInfo());
    }

    /** KILLING -> KILLED after the kill command was sent. */
    public JobRunTransition confirmKilled(Long id, JobRunInfo update) {
        return transition(id, TransitionSpec.CONFIRM_KILLED, update);
    }

    // =================== Observations ===================

    /**
     * Mirrors a status reported by the exec callback or the worker. CREATED and KILLING are not observation targets;
     * such reports return the current row status instead. Terminal rows are never overwritten.
     */
    public JobRunTransition updateFromReport(Long id, ExecutionStatus target, JobRunInfo update) {
        if (target == CREATED || target == KILLING) {
            return rejectedWithCurrentStatus(id);
        }

        // A terminal observation may settle a KILLING row; a non-terminal one must not revive it.
        var expected = target.isTerminalState() ? UNFINISHED_STATUSES : OBSERVED_STATUSES;
        return attemptTransition(id, expected, target, update);
    }

    private JobRunTransition transition(Long id, TransitionSpec spec, JobRunInfo update) {
        return attemptTransition(id, spec.expected, spec.target, update);
    }

    private JobRunTransition attemptTransition(
            Long id, List<ExecutionStatus> expected, ExecutionStatus target, JobRunInfo update) {
        update.setStatus(target);
        if (jobRunService.updateStatusFromAnyTo(id, expected, update) == 1) {
            return JobRunTransition.applied(target);
        }

        return rejectedWithCurrentStatus(id);
    }

    private JobRunTransition rejectedWithCurrentStatus(Long id) {
        var run = jobRunService.getLiteById(id);
        return run == null ? JobRunTransition.notFound() : JobRunTransition.rejected(run.getStatus());
    }

    private enum TransitionSpec {
        SUBMIT_CREATED(List.of(CREATED), SUBMITTED),
        COMPLETE_WITH_ERROR(List.of(CREATED, SUBMITTED), ERROR),
        REQUEST_KILL(List.of(CREATED, WAITING, SUBMITTED, RUNNING), KILLING),
        CONFIRM_KILLED(List.of(KILLING), KILLED);

        private final List<ExecutionStatus> expected;

        private final ExecutionStatus target;

        TransitionSpec(List<ExecutionStatus> expected, ExecutionStatus target) {
            this.expected = expected;
            this.target = target;
        }
    }
}
