package com.flink.platform.web.runner;

import com.flink.platform.alert.AlertSendingService;
import com.flink.platform.dao.entity.Dependency;
import com.flink.platform.dao.entity.DependencyConfig;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.quartz.QuartzService;
import com.flink.platform.web.service.DependencyService;
import com.flink.platform.web.service.DependencyService.DependencyState;
import com.flink.platform.web.service.FlowScheduleService;
import com.flink.platform.web.service.JobFlowRunBizService;
import com.flink.platform.web.service.KillJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.RUNNING;
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
import static org.mockito.Mockito.times;
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
    private JobFlowService jobFlowService;

    @Mock
    private JobFlowRunBizService flowRunBizService;

    @Mock
    private AlertSendingService alertSendingService;

    @Mock
    private KillJobService killJobService;

    @Mock
    private DependencyService dependencyService;

    /** Real: nextTriggerTimeAfter is pure cron arithmetic and never touches the scheduler. */
    private final QuartzService quartzService = new QuartzService();

    private FlowScheduleService flowScheduleService;

    private FlowRunDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        lenient().when(workerConfig.getFlowExecThreads()).thenReturn(4);
        lenient().when(workerConfig.getFlowWaitTimeout()).thenReturn(Duration.ofHours(24));
        lenient().when(workerConfig.getFlowWaitWarningAfter()).thenReturn(Duration.ofHours(1));
        flowScheduleService = new FlowScheduleService(
                jobFlowService, jobFlowRunService, flowRunBizService, quartzService, workerConfig);
        dispatcher = new FlowRunDispatcher(
                workerConfig,
                jobFlowRunService,
                alertSendingService,
                killJobService,
                dependencyService,
                flowScheduleService);
    }

    @Test
    void waitNotTimedOutWithinFallbackWindow() {
        var run = waitingRun();
        var waitStartedAt = LocalDateTime.now().minusHours(1);
        run.setScheduleTime(waitStartedAt);
        setCreateTime(run, waitStartedAt);
        assertFalse(hasWaitTimedOut(run));
    }

    @Test
    void waitTimedOutBeyondFallbackWindow() {
        var run = waitingRun();
        var waitStartedAt = LocalDateTime.now().minusHours(25);
        run.setScheduleTime(waitStartedAt);
        setCreateTime(run, waitStartedAt);
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
        var scheduleTime = LocalDateTime.now().minusMinutes(5);
        run.setScheduleTime(scheduleTime);
        setCreateTime(run, scheduleTime);
        // next fire after (now-5min) is ~4min in the past → timed out
        assertTrue(hasWaitTimedOut(run));
    }

    @Test
    void cronScheduleUsesNextFireAsDeadline() {
        var run = waitingRun();
        run.setCronExpr("0 * * * * ?");
        var scheduleTime = LocalDateTime.of(2026, 1, 1, 10, 0);

        run.setScheduleTime(scheduleTime);
        setCreateTime(run, scheduleTime);
        assertEquals(scheduleTime.plusMinutes(1), flowScheduleService.resolveWaitDeadline(run));
    }

    @Test
    void historicalCronRerunUsesNextLiveFireInsteadOfDefaultTimeout() {
        var run = waitingRun();
        run.setCronExpr("0 0 * * * ?");
        run.setScheduleTime(LocalDateTime.of(2025, 1, 1, 0, 0));
        var waitStartedAt = LocalDateTime.of(2026, 1, 10, 10, 15);
        setCreateTime(run, waitStartedAt);

        lenient().when(workerConfig.getFlowWaitTimeout()).thenReturn(Duration.ofMinutes(30));
        assertEquals(LocalDateTime.of(2026, 1, 10, 11, 0), flowScheduleService.resolveWaitDeadline(run));
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
        when(jobFlowRunService.updateStatusFromTo(1L, WAITING, FAILURE)).thenReturn(true);

        dispatcher.advanceWaitingRuns();

        var reason = ArgumentCaptor.forClass(String.class);
        verify(alertSendingService).sendAlerts(eq(run), reason.capture());
        assertEquals(FAILURE, run.getStatus());
        assertNotNull(run.getEndTime());
        assertTrue(reason.getValue().contains("Failed"), reason.getValue());
        verify(dependencyService, never()).evaluate(any());
    }

    /** A run whose dependencies are simply slow keeps waiting until its deadline. */
    @Test
    void pendingRunBeforeDeadlineIsLeftAlone() {
        var run = waitingRun();
        var waitStartedAt = LocalDateTime.now().minusMinutes(30);
        run.setScheduleTime(waitStartedAt);
        setCreateTime(run, waitStartedAt);
        when(jobFlowRunService.listWaitingRunsOnHost()).thenReturn(List.of(run));
        when(dependencyService.evaluate(any())).thenReturn(DependencyState.PENDING);

        dispatcher.advanceWaitingRuns();

        verify(jobFlowRunService, never()).updateStatusFromTo(anyLong(), any(), any());
        verify(alertSendingService, never()).sendAlertsDirectly(any(), any());
    }

    @Test
    void backfillWaitDeadlineStartsAtCreationRatherThanLogicalScheduleTime() {
        lenient().when(workerConfig.getFlowWaitTimeout()).thenReturn(Duration.ofHours(24));
        var run = waitingRun();
        run.setScheduleTime(LocalDateTime.now().minusDays(30));
        setCreateTime(run, LocalDateTime.now().minusMinutes(5));
        when(jobFlowRunService.listWaitingRunsOnHost()).thenReturn(List.of(run));
        when(dependencyService.evaluate(any())).thenReturn(DependencyState.PENDING);

        dispatcher.advanceWaitingRuns();

        verify(jobFlowRunService, never()).updateStatusFromTo(anyLong(), any(), any());
    }

    @Test
    void delayedPendingRunWarnsOnlyOnce() {
        var run = waitingRunWithDependency();
        var waitStartedAt = LocalDateTime.now().minusHours(2);
        run.setScheduleTime(waitStartedAt);
        setCreateTime(run, waitStartedAt);
        when(jobFlowRunService.listWaitingRunsOnHost()).thenReturn(List.of(run));
        when(dependencyService.evaluate(any())).thenReturn(DependencyState.PENDING);

        dispatcher.advanceWaitingRuns();
        dispatcher.advanceWaitingRuns();

        var message = ArgumentCaptor.forClass(String.class);
        verify(alertSendingService, times(1)).sendAlertsDirectly(eq(run), message.capture());
        assertTrue(message.getValue().contains("flowId=100"), message.getValue());
        assertTrue(message.getValue().contains("latestRunId=200"), message.getValue());
        assertTrue(message.getValue().contains("status=RUNNING"), message.getValue());
        assertFalse(message.getValue().contains("\n"), message.getValue());
    }

    @Test
    void waitWindowShorterThanWarningDelayDoesNotWarn() {
        lenient().when(workerConfig.getFlowWaitTimeout()).thenReturn(Duration.ofMinutes(30));
        var run = waitingRunWithDependency();
        var waitStartedAt = LocalDateTime.now().minusMinutes(10);
        run.setScheduleTime(waitStartedAt);
        setCreateTime(run, waitStartedAt);
        when(jobFlowRunService.listWaitingRunsOnHost()).thenReturn(List.of(run));
        when(dependencyService.evaluate(any())).thenReturn(DependencyState.PENDING);

        dispatcher.advanceWaitingRuns();

        verify(alertSendingService, never()).sendAlertsDirectly(any(), any());
    }

    @Test
    void timedOutRunFailsWithoutDelayedWarning() {
        var run = waitingRunWithDependency();
        var waitStartedAt = LocalDateTime.now().minusHours(25);
        run.setScheduleTime(waitStartedAt);
        setCreateTime(run, waitStartedAt);
        when(jobFlowRunService.listWaitingRunsOnHost()).thenReturn(List.of(run));
        when(jobFlowRunService.updateStatusFromTo(1L, WAITING, FAILURE)).thenReturn(true);

        dispatcher.advanceWaitingRuns();

        verify(alertSendingService).sendAlerts(eq(run), any());
        verify(alertSendingService, never()).sendAlertsDirectly(any(), any());
        verify(dependencyService, never()).evaluate(any());
    }

    @Test
    void expiredRunFailsBeforeItsDependencyIsEvaluated() {
        var run = waitingRun();
        var waitStartedAt = LocalDateTime.now().minusHours(25);
        run.setScheduleTime(waitStartedAt);
        setCreateTime(run, waitStartedAt);
        when(jobFlowRunService.listWaitingRunsOnHost()).thenReturn(List.of(run));
        when(jobFlowRunService.updateStatusFromTo(1L, WAITING, FAILURE)).thenReturn(true);

        dispatcher.advanceWaitingRuns();

        verify(jobFlowRunService).updateStatusFromTo(1L, WAITING, FAILURE);
        verify(jobFlowRunService, never()).updateStatusFromTo(eq(List.of(1L)), eq(WAITING), eq(SUBMITTED));
        verify(dependencyService, never()).evaluate(any());
        verify(alertSendingService).sendAlerts(eq(run), any());
    }

    @Test
    void satisfiedRunsAreAdvancedInOneConditionalBatch() {
        var first = waitingRun();
        var second = waitingRun();
        second.setId(2L);
        when(jobFlowRunService.listWaitingRunsOnHost()).thenReturn(List.of(first, second));
        when(dependencyService.evaluate(any())).thenReturn(DependencyState.SATISFIED);
        when(jobFlowRunService.updateStatusFromTo(List.of(1L, 2L), WAITING, SUBMITTED))
                .thenReturn(2);

        dispatcher.advanceWaitingRuns();

        verify(jobFlowRunService).updateStatusFromTo(List.of(1L, 2L), WAITING, SUBMITTED);
        verify(jobFlowRunService, never()).updateStatusFromTo(anyLong(), eq(WAITING), eq(SUBMITTED));
    }

    @Test
    void satisfiedRunsAdvanceBeforeFailuresAreProcessed() {
        var satisfied = waitingRun();
        var failed = waitingRun();
        failed.setId(2L);
        when(jobFlowRunService.listWaitingRunsOnHost()).thenReturn(List.of(satisfied, failed));
        when(dependencyService.evaluate(any())).thenReturn(DependencyState.SATISFIED, DependencyState.UNSATISFIABLE);
        when(jobFlowRunService.updateStatusFromTo(List.of(1L), WAITING, SUBMITTED))
                .thenReturn(1);
        when(jobFlowRunService.updateStatusFromTo(2L, WAITING, FAILURE)).thenReturn(true);

        dispatcher.advanceWaitingRuns();

        var ordered = inOrder(jobFlowRunService);
        ordered.verify(jobFlowRunService).updateStatusFromTo(List.of(1L), WAITING, SUBMITTED);
        ordered.verify(jobFlowRunService).updateStatusFromTo(2L, WAITING, FAILURE);
        verify(alertSendingService).sendAlerts(eq(failed), any());
    }

    @Test
    void concurrentUpdateOfOneFailureDoesNotSkipRemainingFailures() {
        var concurrentlyUpdated = waitingRun();
        var failed = waitingRun();
        failed.setId(2L);
        when(jobFlowRunService.listWaitingRunsOnHost()).thenReturn(List.of(concurrentlyUpdated, failed));
        when(dependencyService.evaluate(any())).thenReturn(DependencyState.UNSATISFIABLE);
        when(jobFlowRunService.updateStatusFromTo(1L, WAITING, FAILURE)).thenReturn(false);
        when(jobFlowRunService.updateStatusFromTo(2L, WAITING, FAILURE)).thenReturn(true);

        dispatcher.advanceWaitingRuns();

        verify(jobFlowRunService).updateStatusFromTo(1L, WAITING, FAILURE);
        verify(jobFlowRunService).updateStatusFromTo(2L, WAITING, FAILURE);
        verify(alertSendingService).sendAlerts(eq(failed), any());
    }

    /** Resolves whether a waiting run is currently past its deadline. */
    private boolean hasWaitTimedOut(JobFlowRun run) {
        return LocalDateTime.now().isAfter(flowScheduleService.resolveWaitDeadline(run));
    }

    private JobFlowRun waitingRun() {
        var run = new JobFlowRun();
        run.setId(1L);
        run.setStatus(WAITING);
        var now = LocalDateTime.now();
        run.setScheduleTime(now);
        setCreateTime(run, now);
        return run;
    }

    private void setCreateTime(JobFlowRun run, LocalDateTime createTime) {
        ReflectionTestUtils.setField(run, "createTime", createTime);
    }

    private JobFlowRun waitingRunWithDependency() {
        var dependency = new Dependency();
        dependency.setFlowId(100L);
        dependency.setLatestFlowRunId(200L);
        dependency.setLatestStatus(RUNNING);
        var config = new DependencyConfig();
        config.setDependencies(List.of(dependency));

        var run = waitingRun();
        run.setDependsOn(config);
        return run;
    }
}
