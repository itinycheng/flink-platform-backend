package com.flink.platform.web.flowrun;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.service.KillJobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.flink.platform.common.enums.ExecutionStatus.KILLED;
import static com.flink.platform.common.enums.ExecutionStatus.KILLING;
import static com.flink.platform.common.enums.ExecutionStatus.RUNNING;
import static com.flink.platform.web.flowrun.FlowRunTransition.Outcome.APPLIED;
import static com.flink.platform.web.flowrun.FlowRunTransition.Outcome.REJECTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowRunKillServiceTest {

    private static final long RUN_ID = 1L;

    @Mock
    private FlowRunStateService stateService;

    @Mock
    private JobFlowRunService flowRunService;

    @Mock
    private JobRunInfoService jobRunService;

    @Mock
    private KillJobService jobKillService;

    @InjectMocks
    private FlowRunKillService service;

    @Test
    void directKillDoesNotRepeatCleanupAfterTerminalTransition() {
        when(stateService.requestKill(RUN_ID))
                .thenReturn(FlowRunTransition.applied(KILLED), FlowRunTransition.rejected(KILLED));

        assertEquals(APPLIED, service.requestKill(RUN_ID).outcome());
        assertEquals(REJECTED, service.requestKill(RUN_ID).outcome());
        verify(stateService, times(2)).requestKill(RUN_ID);
        verifyNoInteractions(jobRunService);
    }

    @Test
    void requestKillWithoutChildrenFinalizesImmediately() {
        when(stateService.requestKill(RUN_ID)).thenReturn(FlowRunTransition.applied(KILLING));
        when(stateService.forceKilled(RUN_ID)).thenReturn(FlowRunTransition.applied(KILLED));
        when(jobRunService.listLiteUnfinishedRuns(RUN_ID)).thenReturn(List.of());

        var result = service.requestKill(RUN_ID);

        assertEquals(APPLIED, result.outcome());
        assertEquals(KILLING, result.status());
        verify(stateService).forceKilled(RUN_ID);
    }

    @Test
    void returnsRejectedTransitionWithoutCleanup() {
        when(stateService.requestKill(RUN_ID)).thenReturn(FlowRunTransition.rejected(RUNNING));

        var result = service.requestKill(RUN_ID);

        assertEquals(REJECTED, result.outcome());
        assertEquals(RUNNING, result.status());
        verify(stateService, times(1)).requestKill(RUN_ID);
        verifyNoInteractions(jobRunService);
    }

    @Test
    void killingRunWaitsWhileChildIsStillActive() {
        var child = new JobRunInfo();
        child.setId(42L);
        when(flowRunService.getLiteById(RUN_ID)).thenReturn(runWith(KILLING));
        when(jobRunService.listLiteUnfinishedRuns(RUN_ID)).thenReturn(List.of(child));
        when(jobKillService.attemptToKillJob(child)).thenReturn(false);

        assertFalse(service.advanceKilling(RUN_ID));

        verify(jobKillService).attemptToKillJob(child);
        verify(stateService, never()).confirmKilled(RUN_ID);
    }

    @Test
    void killingRunConfirmsAfterChildBecomesTerminal() {
        var child = new JobRunInfo();
        child.setId(42L);
        when(flowRunService.getLiteById(RUN_ID)).thenReturn(runWith(KILLING));
        when(jobRunService.listLiteUnfinishedRuns(RUN_ID)).thenReturn(List.of(child), List.of());
        when(jobKillService.attemptToKillJob(child)).thenReturn(true);
        when(stateService.confirmKilled(RUN_ID)).thenReturn(FlowRunTransition.applied(KILLED));

        assertTrue(service.advanceKilling(RUN_ID));

        verify(jobKillService).attemptToKillJob(child);
        verify(stateService).confirmKilled(RUN_ID);
    }

    private JobFlowRun runWith(ExecutionStatus status) {
        var run = new JobFlowRun();
        run.setStatus(status);
        return run;
    }
}
