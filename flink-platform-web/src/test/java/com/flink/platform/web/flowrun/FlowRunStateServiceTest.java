package com.flink.platform.web.flowrun;

import com.flink.platform.alert.AlertSendingService;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static com.flink.platform.common.enums.ExecutionStatus.CREATED;
import static com.flink.platform.common.enums.ExecutionStatus.EXPECTED_FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.KILLED;
import static com.flink.platform.common.enums.ExecutionStatus.KILLING;
import static com.flink.platform.common.enums.ExecutionStatus.RUNNING;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static com.flink.platform.common.enums.ExecutionStatus.WAITING;
import static com.flink.platform.web.flowrun.FlowRunTransition.Outcome.APPLIED;
import static com.flink.platform.web.flowrun.FlowRunTransition.Outcome.NOT_FOUND;
import static com.flink.platform.web.flowrun.FlowRunTransition.Outcome.REJECTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Verifies which conditional update each transition issues, without a database. */
@ExtendWith(MockitoExtension.class)
class FlowRunStateServiceTest {

    private static final long RUN_ID = 1L;
    private static final String HOST = "worker-a";
    private static final Set<ExecutionStatus> NON_TERMINAL = Set.of(CREATED, WAITING, SUBMITTED, RUNNING, KILLING);

    @Mock
    private JobFlowRunService flowRunService;

    @Mock
    private AlertSendingService alertSendingService;

    @InjectMocks
    private FlowRunStateService service;

    @Test
    void waitingTransitionsAreHostAgnostic() {
        when(flowRunService.updateStatusFromAnyTo(eq(RUN_ID), eq(Set.of(WAITING)), isNull(), eq(SUBMITTED)))
                .thenReturn(1);
        when(flowRunService.updateStatusFromAnyTo(eq(RUN_ID), eq(Set.of(WAITING)), isNull(), eq(FAILURE)))
                .thenReturn(1);

        assertEquals(APPLIED, service.submitWaiting(RUN_ID).outcome());
        assertEquals(APPLIED, service.failWaiting(RUN_ID).outcome());
    }

    @Test
    void failSubmittedIsHostAgnostic() {
        when(flowRunService.updateStatusFromAnyTo(eq(RUN_ID), eq(Set.of(SUBMITTED)), isNull(), eq(FAILURE)))
                .thenReturn(1);

        assertEquals(APPLIED, service.failSubmitted(RUN_ID).outcome());
    }

    @Test
    void startRunningRequiresTheOwnerHost() {
        when(flowRunService.updateStatusFromAnyTo(eq(RUN_ID), eq(Set.of(SUBMITTED)), eq(HOST), eq(RUNNING)))
                .thenReturn(1);

        assertEquals(APPLIED, service.startRunning(RUN_ID, HOST).outcome());
    }

    @Test
    void finishRunningAcceptsOnlyExecutionOutcomes() {
        for (var target : Set.of(SUCCESS, FAILURE, EXPECTED_FAILURE)) {
            when(flowRunService.updateStatusFromAnyTo(eq(RUN_ID), eq(Set.of(RUNNING)), eq(HOST), eq(target)))
                    .thenReturn(1);

            assertEquals(APPLIED, service.completeRunning(RUN_ID, HOST, target).outcome());
        }

        assertThrows(IllegalArgumentException.class, () -> service.completeRunning(RUN_ID, HOST, KILLED));
    }

    @Test
    void requestKillWithHostRequiresTheOwnerHost() {
        when(flowRunService.updateStatusFromAnyTo(eq(RUN_ID), eq(Set.of(RUNNING)), eq(HOST), eq(KILLING)))
                .thenReturn(1);

        assertEquals(APPLIED, service.requestKill(RUN_ID, HOST).outcome());
    }

    @Test
    void requestKillFinalizesRunsThatNeverStarted() {
        when(flowRunService.getLiteById(RUN_ID)).thenReturn(runWith(SUBMITTED));
        when(flowRunService.updateStatusFromAnyTo(
                        eq(RUN_ID), eq(Set.of(CREATED, WAITING, SUBMITTED)), isNull(), eq(KILLED)))
                .thenReturn(1);

        assertEquals(APPLIED, service.requestKill(RUN_ID).outcome());
    }

    @Test
    void requestKillMarksRunningRunForCleanup() {
        when(flowRunService.getLiteById(RUN_ID)).thenReturn(runWith(RUNNING));
        when(flowRunService.updateStatusFromAnyTo(eq(RUN_ID), eq(Set.of(RUNNING)), isNull(), eq(KILLING)))
                .thenReturn(1);

        var transition = service.requestKill(RUN_ID);

        assertEquals(APPLIED, transition.outcome());
        assertEquals(KILLING, transition.status());
    }

    @Test
    void requestKillRetriesWhenStartClaimWinsTheFirstCas() {
        when(flowRunService.getLiteById(RUN_ID)).thenReturn(runWith(SUBMITTED), runWith(RUNNING), runWith(RUNNING));
        when(flowRunService.updateStatusFromAnyTo(
                        eq(RUN_ID), eq(Set.of(CREATED, WAITING, SUBMITTED)), isNull(), eq(KILLED)))
                .thenReturn(0);
        when(flowRunService.updateStatusFromAnyTo(eq(RUN_ID), eq(Set.of(RUNNING)), isNull(), eq(KILLING)))
                .thenReturn(1);

        var transition = service.requestKill(RUN_ID);

        assertEquals(APPLIED, transition.outcome());
        assertEquals(KILLING, transition.status());
        verify(flowRunService).updateStatusFromAnyTo(RUN_ID, Set.of(RUNNING), null, KILLING);
    }

    @Test
    void requestKillKeepsTheWinningStatus() {
        when(flowRunService.getLiteById(RUN_ID)).thenReturn(runWith(KILLING));
        assertEquals(REJECTED, service.requestKill(RUN_ID).outcome());
        assertEquals(KILLING, service.requestKill(RUN_ID).status());

        when(flowRunService.getLiteById(RUN_ID)).thenReturn(runWith(SUCCESS));
        assertEquals(REJECTED, service.requestKill(RUN_ID).outcome());
        assertEquals(SUCCESS, service.requestKill(RUN_ID).status());

        verify(flowRunService, never()).updateStatusFromAnyTo(any(), any(), any(), any());
    }

    @Test
    void requestKillReportsMissingRun() {
        when(flowRunService.getLiteById(RUN_ID)).thenReturn(null);

        assertEquals(NOT_FOUND, service.requestKill(RUN_ID).outcome());
        verify(flowRunService, never()).updateStatusFromAnyTo(any(), any(), any(), any());
    }

    @Test
    void confirmKilledRequiresKilling() {
        when(flowRunService.updateStatusFromAnyTo(eq(RUN_ID), eq(Set.of(KILLING)), isNull(), eq(KILLED)))
                .thenReturn(1);

        assertEquals(APPLIED, service.confirmKilled(RUN_ID).outcome());
    }

    @Test
    void administrativeConfirmationOverridesAnyNonTerminalStatus() {
        when(flowRunService.updateStatusFromAnyTo(eq(RUN_ID), eq(NON_TERMINAL), isNull(), eq(KILLED)))
                .thenReturn(1);

        assertEquals(APPLIED, service.forceKilled(RUN_ID).outcome());
    }

    @Test
    void lostCasReportsTheCurrentStatus() {
        when(flowRunService.updateStatusFromAnyTo(eq(RUN_ID), eq(Set.of(WAITING)), isNull(), eq(SUBMITTED)))
                .thenReturn(0);
        when(flowRunService.getLiteById(RUN_ID)).thenReturn(runWith(SUCCESS));

        var transition = service.submitWaiting(RUN_ID);

        assertEquals(REJECTED, transition.outcome());
        assertEquals(SUCCESS, transition.status());
    }

    @Test
    void lostCasWithMissingRowReportsNotFound() {
        when(flowRunService.updateStatusFromAnyTo(eq(RUN_ID), eq(Set.of(WAITING)), isNull(), eq(SUBMITTED)))
                .thenReturn(0);
        when(flowRunService.getLiteById(RUN_ID)).thenReturn(null);

        assertEquals(NOT_FOUND, service.submitWaiting(RUN_ID).outcome());
    }

    @Test
    void appliedTerminalTransitionSendsAlertWithReason() {
        var failed = runWith(FAILURE);
        when(flowRunService.updateStatusFromAnyTo(eq(RUN_ID), eq(Set.of(WAITING)), isNull(), eq(FAILURE)))
                .thenReturn(1);
        when(flowRunService.getById(RUN_ID)).thenReturn(failed);

        assertEquals(
                APPLIED, service.failWaiting(RUN_ID, "dependency unsatisfiable").outcome());

        verify(alertSendingService).sendAlerts(failed, "dependency unsatisfiable");
    }

    @Test
    void nonTerminalTransitionDoesNotSendAlert() {
        when(flowRunService.getLiteById(RUN_ID)).thenReturn(runWith(RUNNING));
        when(flowRunService.updateStatusFromAnyTo(eq(RUN_ID), eq(Set.of(RUNNING)), isNull(), eq(KILLING)))
                .thenReturn(1);

        assertEquals(APPLIED, service.requestKill(RUN_ID).outcome());

        verifyNoInteractions(alertSendingService);
    }

    @Test
    void alertIsSkippedWhenTheRunMovedOnBeforeDelivery() {
        when(flowRunService.updateStatusFromAnyTo(eq(RUN_ID), eq(Set.of(WAITING)), isNull(), eq(FAILURE)))
                .thenReturn(1);
        when(flowRunService.getById(RUN_ID)).thenReturn(runWith(KILLED));

        assertEquals(
                APPLIED, service.failWaiting(RUN_ID, "dependency unsatisfiable").outcome());

        verifyNoInteractions(alertSendingService);
    }

    private JobFlowRun runWith(ExecutionStatus status) {
        var run = new JobFlowRun();
        run.setId(RUN_ID);
        run.setStatus(status);
        return run;
    }
}
