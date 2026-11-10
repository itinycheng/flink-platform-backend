package com.flink.platform.web.flowrun;

import com.flink.platform.alert.AlertSendingService;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.util.Preconditions;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.web.flowrun.FlowRunTransition.Outcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Set;

import static com.flink.platform.common.constants.Constant.HOST_IP;
import static com.flink.platform.common.enums.ExecutionStatus.CREATED;
import static com.flink.platform.common.enums.ExecutionStatus.EXPECTED_FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.KILLED;
import static com.flink.platform.common.enums.ExecutionStatus.KILLING;
import static com.flink.platform.common.enums.ExecutionStatus.RUNNING;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static com.flink.platform.common.enums.ExecutionStatus.WAITING;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

/** Owns conditional transitions for every flow-run type. */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowRunStateService {

    private final JobFlowRunService flowRunService;

    private final AlertSendingService alertSendingService;

    public FlowRunTransition submitWaiting(Long id) {
        return transition(id, TransitionSpec.SUBMIT_WAITING);
    }

    public FlowRunTransition failWaiting(Long id) {
        return failWaiting(id, "");
    }

    public FlowRunTransition failWaiting(Long id, String reason) {
        return transition(id, TransitionSpec.FAIL_WAITING, reason);
    }

    public FlowRunTransition failSubmitted(Long id) {
        return failSubmitted(id, "");
    }

    public FlowRunTransition failSubmitted(Long id, String reason) {
        return transition(id, TransitionSpec.FAIL_SUBMITTED, reason);
    }

    public FlowRunTransition startRunning(Long id, String workerHost) {
        return transitionOwned(id, TransitionSpec.START_RUNNING, workerHost);
    }

    public FlowRunTransition completeRunning(Long id, String workerHost, ExecutionStatus target) {
        return transitionOwned(id, TransitionSpec.completion(target), workerHost);
    }

    public FlowRunTransition requestKill(Long id, String workerHost) {
        return transitionOwned(id, TransitionSpec.REQUEST_KILL_RUNNING, workerHost);
    }

    public FlowRunTransition requestKill(Long id) {
        var result = requestKillOnce(id);
        // Retry once when the first CAS loses to a concurrent transition.
        if (result.outcome() == Outcome.REJECTED && TransitionSpec.requestKillFor(result.status()) != null) {
            result = requestKillOnce(id);
        }
        return result;
    }

    public FlowRunTransition confirmKilled(Long id) {
        return transition(id, TransitionSpec.CONFIRM_KILLED);
    }

    public FlowRunTransition forceKilled(Long id) {
        return transition(id, TransitionSpec.FORCE_KILLED);
    }

    // ====================================================
    // ====================== private =====================
    // ====================================================

    /** For transitions justified by durable, cluster-wide facts; any worker may apply them. */
    private FlowRunTransition transition(Long id, TransitionSpec transitionSpec) {
        return transition(id, transitionSpec, "");
    }

    private FlowRunTransition transition(Long id, TransitionSpec transitionSpec, String alertMsg) {
        Preconditions.checkArgument(id > 0, "flow run id must be positive");
        var result = attemptTransition(id, null, transitionSpec);
        logTransition(id, null, transitionSpec, result);
        sendTerminalAlert(id, result, alertMsg);
        return result;
    }

    private FlowRunTransition requestKillOnce(Long id) {
        var state = flowRunService.getLiteById(id);
        if (state == null) {
            return FlowRunTransition.notFound();
        }

        var spec = TransitionSpec.requestKillFor(state.getStatus());
        return spec != null ? transition(id, spec) : FlowRunTransition.rejected(state.getStatus());
    }

    /** For transitions that assert a local fact; only the owning worker may apply them. */
    private FlowRunTransition transitionOwned(Long id, TransitionSpec transitionSpec, String ownerHost) {
        Preconditions.checkArgument(id > 0, "flow run id must be positive");
        Preconditions.checkArgument(!ownerHost.isBlank(), "Worker host must not be blank");
        var result = attemptTransition(id, ownerHost, transitionSpec);
        logTransition(id, ownerHost, transitionSpec, result);
        sendTerminalAlert(id, result, "");
        return result;
    }

    private void sendTerminalAlert(Long id, FlowRunTransition transition, String message) {
        if (transition.outcome() != Outcome.APPLIED
                || transition.status() == null
                || !transition.status().isTerminalState()) {
            return;
        }
        runAfterCommitOrNow(newTerminalAlertTask(id, transition, message));
    }

    private FlowRunTransition attemptTransition(Long id, @Nullable String expectedHost, TransitionSpec transitionSpec) {
        if (flowRunService.updateStatusFromAnyTo(id, transitionSpec.expected, expectedHost, transitionSpec.target)
                == 1) {
            return FlowRunTransition.applied(transitionSpec.target);
        }

        var state = flowRunService.getLiteById(id);
        return state == null ? FlowRunTransition.notFound() : FlowRunTransition.rejected(state.getStatus());
    }

    private void logTransition(
            Long id, @Nullable String expectedHost, TransitionSpec transitionSpec, FlowRunTransition result) {
        if (!log.isInfoEnabled()) {
            return;
        }

        Runnable task = () -> log.info(
                "Flow-run transition flowRunId={} operation={} expectedStatuses={} expectedHost={} "
                        + "targetStatus={} outcome={} status={} worker={}",
                id,
                transitionSpec.operation,
                transitionSpec.expectedSorted,
                defaultIfNull(expectedHost, "unknown"),
                transitionSpec.target.name(),
                result.outcome(),
                defaultIfNull(result.status(), "unknown"),
                HOST_IP);
        runAfterCommitOrNow(task);
    }

    private Runnable newTerminalAlertTask(Long id, FlowRunTransition transition, String message) {
        var terminalStatus = transition.status();
        return () -> {
            try {
                var flowRun = flowRunService.getById(id);
                if (flowRun == null || flowRun.getStatus() != terminalStatus) {
                    log.warn(
                            "Skip terminal alert for flow run {}: expected {}, actual {}",
                            id,
                            terminalStatus,
                            flowRun == null ? null : flowRun.getStatus());
                    return;
                }
                alertSendingService.sendAlerts(flowRun, message);
            } catch (Exception e) {
                log.error("Failed to send terminal alert for flow run {}", id, e);
            }
        };
    }

    private void runAfterCommitOrNow(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private enum TransitionSpec {
        SUBMIT_WAITING("submit_waiting", Set.of(WAITING), SUBMITTED),
        FAIL_WAITING("fail_waiting", Set.of(WAITING), FAILURE),
        FAIL_SUBMITTED("fail_submitted", Set.of(SUBMITTED), FAILURE),
        START_RUNNING("start", Set.of(SUBMITTED), RUNNING),
        COMPLETE_SUCCESS("complete", Set.of(RUNNING), SUCCESS),
        COMPLETE_FAILURE("complete", Set.of(RUNNING), FAILURE),
        COMPLETE_EXPECTED_FAILURE("complete", Set.of(RUNNING), EXPECTED_FAILURE),
        REQUEST_KILL_BEFORE_RUNNING("request_kill", Set.of(CREATED, WAITING, SUBMITTED), KILLED),
        REQUEST_KILL_RUNNING("request_kill", Set.of(RUNNING), KILLING),
        CONFIRM_KILLED("confirm_killed", Set.of(KILLING), KILLED),
        FORCE_KILLED("force_killed", Set.of(CREATED, WAITING, SUBMITTED, RUNNING, KILLING), KILLED);

        private static final List<TransitionSpec> REQUEST_KILL_TRANSITIONS =
                List.of(REQUEST_KILL_BEFORE_RUNNING, REQUEST_KILL_RUNNING);

        private final String operation;

        private final Set<ExecutionStatus> expected;

        private final List<ExecutionStatus> expectedSorted;

        private final ExecutionStatus target;

        TransitionSpec(String operation, Set<ExecutionStatus> expected, ExecutionStatus target) {
            this.operation = operation;
            this.expected = expected;
            this.expectedSorted = expected.stream().sorted().toList();
            this.target = target;
        }

        static @Nullable TransitionSpec requestKillFor(@Nullable ExecutionStatus status) {
            if (status == null) {
                return null;
            }

            return REQUEST_KILL_TRANSITIONS.stream()
                    .filter(spec -> spec.expected.contains(status))
                    .findFirst()
                    .orElse(null);
        }

        static TransitionSpec completion(ExecutionStatus target) {
            return switch (target) {
                case SUCCESS -> COMPLETE_SUCCESS;
                case FAILURE -> COMPLETE_FAILURE;
                case EXPECTED_FAILURE -> COMPLETE_EXPECTED_FAILURE;
                default ->
                    throw new IllegalArgumentException(
                            "Completion target must be SUCCESS, FAILURE, or EXPECTED_FAILURE");
            };
        }
    }
}
