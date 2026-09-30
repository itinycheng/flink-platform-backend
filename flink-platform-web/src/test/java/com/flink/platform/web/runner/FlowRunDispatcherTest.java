package com.flink.platform.web.runner;

import com.flink.platform.alert.AlertSendingService;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.quartz.QuartzService;
import com.flink.platform.web.service.DependencyService;
import com.flink.platform.web.service.DependencyService.DependencyState;
import com.flink.platform.web.service.KillJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static com.flink.platform.common.enums.ExecutionStatus.WAITING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifies how long a WAITING flow run is allowed to wait. Dependency evaluation is covered separately. */
@ExtendWith(MockitoExtension.class)
class FlowRunDispatcherTest {

    @Mock
    private WorkerConfig workerConfig;

    @Mock
    private JobFlowRunService jobFlowRunService;

    @Mock
    private AlertSendingService alertSendingService;

    @Mock
    private KillJobService killJobService;

    @Mock
    private DependencyService dependencyService;

    /** Real: nextTriggerTimeAfter is pure cron arithmetic and never touches the scheduler. */
    private final QuartzService quartzService = new QuartzService();

    private FlowRunDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        lenient().when(workerConfig.getFlowExecThreads()).thenReturn(4);
        dispatcher = new FlowRunDispatcher(
                workerConfig, jobFlowRunService, alertSendingService, killJobService, dependencyService, quartzService);
    }

    @Test
    void waitNotTimedOutWithinFallbackWindow() {
        lenient().when(workerConfig.getFlowWaitTimeout()).thenReturn(Duration.ofHours(24));
        var run = waitingRun();
        run.setScheduleTime(LocalDateTime.now().minusHours(1));
        assertFalse(hasWaitTimedOut(run));
    }

    @Test
    void waitTimedOutBeyondFallbackWindow() {
        lenient().when(workerConfig.getFlowWaitTimeout()).thenReturn(Duration.ofHours(24));
        var run = waitingRun();
        run.setScheduleTime(LocalDateTime.now().minusHours(25));
        assertTrue(hasWaitTimedOut(run));
    }

    @Test
    void waitNotTimedOutBeforeNextCronFire() {
        var run = waitingRun();
        run.setCronExpr("0 * * * * ?"); // every minute
        run.setScheduleTime(LocalDateTime.now().plusMinutes(1));
        // The adjusted deadline remains in the future.
        assertFalse(hasWaitTimedOut(run));
    }

    @Test
    void waitTimedOutAfterNextCronFire() {
        var run = waitingRun();
        run.setCronExpr("0 * * * * ?"); // every minute
        run.setScheduleTime(LocalDateTime.now().minusMinutes(5));
        // next fire after (now-5min) is ~4min in the past → timed out
        assertTrue(hasWaitTimedOut(run));
    }

    @Test
    void cronScheduleUsesTenSecondLeadTime() {
        var run = waitingRun();
        run.setCronExpr("0 * * * * ?");
        var scheduleTime = LocalDateTime.of(2026, 1, 1, 10, 0);

        assertEquals(scheduleTime.plusSeconds(50), dispatcher.calcWaitDeadline(run, scheduleTime));
    }

    /**
     * An unparsable cron ends the wait immediately with the cause attached, rather than silently
     * degrading to the generic flowWaitTimeout window.
     */
    @Test
    void unusableCronEndsTheWaitWithTheCause() {
        var run = waitingRun();
        run.setCronExpr("not-a-valid-cron");
        run.setScheduleTime(LocalDateTime.now());
        when(jobFlowRunService.listWaitingRunsOnHost()).thenReturn(List.of(run));
        when(dependencyService.evaluate(any())).thenReturn(DependencyState.PENDING);
        when(jobFlowRunService.updateStatusFromWaiting(1L, FAILURE)).thenReturn(true);

        dispatcher.advanceWaitingRuns();

        var reason = ArgumentCaptor.forClass(String.class);
        verify(alertSendingService).sendAlerts(eq(run), reason.capture());
        assertEquals(FAILURE, run.getStatus());
        assertNotNull(run.getEndTime());
        assertTrue(reason.getValue().contains("Failed"), reason.getValue());
    }

    /** A run whose dependencies are simply slow keeps waiting until its deadline. */
    @Test
    void pendingRunBeforeDeadlineIsLeftAlone() {
        lenient().when(workerConfig.getFlowWaitTimeout()).thenReturn(Duration.ofHours(24));
        var run = waitingRun();
        run.setScheduleTime(LocalDateTime.now().minusHours(1));
        when(jobFlowRunService.listWaitingRunsOnHost()).thenReturn(List.of(run));
        when(dependencyService.evaluate(any())).thenReturn(DependencyState.PENDING);

        dispatcher.advanceWaitingRuns();

        verify(jobFlowRunService, never()).updateStatusFromWaiting(anyLong(), any());
    }

    @Test
    void satisfiedRunsAreAdvancedInOneConditionalBatch() {
        var first = waitingRun();
        var second = waitingRun();
        second.setId(2L);
        when(jobFlowRunService.listWaitingRunsOnHost()).thenReturn(List.of(first, second));
        when(dependencyService.evaluate(any())).thenReturn(DependencyState.SATISFIED);
        when(jobFlowRunService.updateStatusFromWaiting(List.of(1L, 2L), SUBMITTED))
                .thenReturn(2);

        dispatcher.advanceWaitingRuns();

        verify(jobFlowRunService).updateStatusFromWaiting(List.of(1L, 2L), SUBMITTED);
        verify(jobFlowRunService, never()).updateStatusFromWaiting(anyLong(), eq(SUBMITTED));
    }

    @Test
    void satisfiedRunsAdvanceBeforeFailuresAreProcessed() {
        var satisfied = waitingRun();
        var failed = waitingRun();
        failed.setId(2L);
        when(jobFlowRunService.listWaitingRunsOnHost()).thenReturn(List.of(satisfied, failed));
        when(dependencyService.evaluate(any())).thenReturn(DependencyState.SATISFIED, DependencyState.UNSATISFIABLE);
        when(jobFlowRunService.updateStatusFromWaiting(List.of(1L), SUBMITTED)).thenReturn(1);
        when(jobFlowRunService.updateStatusFromWaiting(2L, FAILURE)).thenReturn(true);

        dispatcher.advanceWaitingRuns();

        var ordered = inOrder(jobFlowRunService);
        ordered.verify(jobFlowRunService).updateStatusFromWaiting(List.of(1L), SUBMITTED);
        ordered.verify(jobFlowRunService).updateStatusFromWaiting(2L, FAILURE);
        verify(alertSendingService).sendAlerts(eq(failed), any());
    }

    @Test
    void concurrentUpdateOfOneFailureDoesNotSkipRemainingFailures() {
        var concurrentlyUpdated = waitingRun();
        var failed = waitingRun();
        failed.setId(2L);
        when(jobFlowRunService.listWaitingRunsOnHost()).thenReturn(List.of(concurrentlyUpdated, failed));
        when(dependencyService.evaluate(any())).thenReturn(DependencyState.UNSATISFIABLE);
        when(jobFlowRunService.updateStatusFromWaiting(1L, FAILURE)).thenReturn(false);
        when(jobFlowRunService.updateStatusFromWaiting(2L, FAILURE)).thenReturn(true);

        dispatcher.advanceWaitingRuns();

        verify(jobFlowRunService).updateStatusFromWaiting(1L, FAILURE);
        verify(jobFlowRunService).updateStatusFromWaiting(2L, FAILURE);
        verify(alertSendingService).sendAlerts(eq(failed), any());
    }

    /** The deadline check the PENDING branch of advanceWaitingRuns performs inline. */
    private boolean hasWaitTimedOut(JobFlowRun run) {
        return LocalDateTime.now().isAfter(dispatcher.calcWaitDeadline(run, run.getScheduleTime()));
    }

    private JobFlowRun waitingRun() {
        var run = new JobFlowRun();
        run.setId(1L);
        run.setStatus(WAITING);
        return run;
    }
}
