package com.flink.platform.web.runner;

import com.flink.platform.alert.AlertSendingService;
import com.flink.platform.dao.entity.Dependency;
import com.flink.platform.dao.entity.DependencyConfig;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.flowrun.FlowRunBizService;
import com.flink.platform.web.flowrun.FlowRunKillService;
import com.flink.platform.web.flowrun.FlowRunStateService;
import com.flink.platform.web.flowrun.FlowRunTransition;
import com.flink.platform.web.quartz.QuartzService;
import com.flink.platform.web.service.DependencyService;
import com.flink.platform.web.service.DependencyService.DependencyState;
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
class FlowRunCoordinatorTest {

    @Mock
    private WorkerConfig workerConfig;

    @Mock
    private JobFlowRunService jobFlowRunService;

    @Mock
    private AlertSendingService alertSendingService;

    @Mock
    private FlowRunKillService killService;

    @Mock
    private DependencyService dependencyService;

    @Mock
    private FlowRunStateService stateService;

    /** Real: nextTriggerTimeAfter is pure cron arithmetic and never touches the scheduler. */
    private final QuartzService quartzService = new QuartzService();

    private FlowRunBizService bizService;

    private FlowRunCoordinator dispatcher;

    @BeforeEach
    void setUp() {
        lenient().when(workerConfig.getFlowExecThreads()).thenReturn(4);
        lenient().when(workerConfig.getFlowWaitTimeout()).thenReturn(Duration.ofHours(24));
        lenient().when(workerConfig.getFlowWaitWarningAfter()).thenReturn(Duration.ofHours(1));
        bizService = new FlowRunBizService(jobFlowRunService, quartzService, workerConfig);
        dispatcher = new FlowRunCoordinator(
                workerConfig,
                jobFlowRunService,
                bizService,
                alertSendingService,
                killService,
                dependencyService,
                stateService);
    }

    @Test
    void cronScheduleUsesNextFireAsDeadline() {
        var run = waitingRun();
        run.setCronExpr("0 * * * * ?");
        var scheduleTime = LocalDateTime.of(2026, 1, 1, 10, 0);

        run.setScheduleTime(scheduleTime);
        setCreateTime(run, scheduleTime);
        assertEquals(scheduleTime.plusMinutes(1), bizService.resolveWaitDeadline(run));
    }

    @Test
    void historicalCronRerunUsesNextLiveFireInsteadOfDefaultTimeout() {
        var run = waitingRun();
        run.setCronExpr("0 0 * * * ?");
        run.setScheduleTime(LocalDateTime.of(2025, 1, 1, 0, 0));
        var waitStartedAt = LocalDateTime.of(2026, 1, 10, 10, 15);
        setCreateTime(run, waitStartedAt);

        lenient().when(workerConfig.getFlowWaitTimeout()).thenReturn(Duration.ofMinutes(30));
        assertEquals(LocalDateTime.of(2026, 1, 10, 11, 0), bizService.resolveWaitDeadline(run));
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
        when(stateService.failWaiting(eq(1L), any())).thenReturn(FlowRunTransition.applied(FAILURE));

        dispatcher.advanceWaitingRuns();

        var reason = ArgumentCaptor.forClass(String.class);
        verify(stateService).failWaiting(eq(1L), reason.capture());
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

        verify(stateService, never()).submitWaiting(anyLong());
        verify(stateService, never()).failWaiting(anyLong(), any());
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

        verify(stateService, never()).submitWaiting(anyLong());
        verify(stateService, never()).failWaiting(anyLong(), any());
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
        when(stateService.failWaiting(eq(1L), any())).thenReturn(FlowRunTransition.applied(FAILURE));

        dispatcher.advanceWaitingRuns();

        verify(stateService).failWaiting(eq(1L), any());
        verify(alertSendingService, never()).sendAlertsDirectly(any(), any());
        verify(dependencyService, never()).evaluate(any());
    }

    @Test
    void satisfiedRunsAreAdvancedOneByOne() {
        var first = waitingRun();
        var second = waitingRun();
        second.setId(2L);
        when(jobFlowRunService.listWaitingRunsOnHost()).thenReturn(List.of(first, second));
        when(dependencyService.evaluate(any())).thenReturn(DependencyState.SATISFIED);
        when(stateService.submitWaiting(1L)).thenReturn(FlowRunTransition.applied(SUBMITTED));
        when(stateService.submitWaiting(2L)).thenReturn(FlowRunTransition.applied(SUBMITTED));

        dispatcher.advanceWaitingRuns();

        verify(stateService).submitWaiting(1L);
        verify(stateService).submitWaiting(2L);
        verify(stateService, never()).failWaiting(anyLong(), any());
    }

    @Test
    void satisfiedRunsAdvanceBeforeFailuresAreProcessed() {
        var satisfied = waitingRun();
        var failed = waitingRun();
        failed.setId(2L);
        when(jobFlowRunService.listWaitingRunsOnHost()).thenReturn(List.of(satisfied, failed));
        when(dependencyService.evaluate(any())).thenReturn(DependencyState.SATISFIED, DependencyState.UNSATISFIABLE);
        when(stateService.submitWaiting(1L)).thenReturn(FlowRunTransition.applied(SUBMITTED));
        when(stateService.failWaiting(eq(2L), any())).thenReturn(FlowRunTransition.applied(FAILURE));

        dispatcher.advanceWaitingRuns();

        var ordered = inOrder(stateService);
        ordered.verify(stateService).submitWaiting(1L);
        ordered.verify(stateService).failWaiting(eq(2L), any());
    }

    @Test
    void concurrentUpdateOfOneFailureDoesNotSkipRemainingFailures() {
        var concurrentlyUpdated = waitingRun();
        var failed = waitingRun();
        failed.setId(2L);
        when(jobFlowRunService.listWaitingRunsOnHost()).thenReturn(List.of(concurrentlyUpdated, failed));
        when(dependencyService.evaluate(any())).thenReturn(DependencyState.UNSATISFIABLE);
        when(stateService.failWaiting(eq(1L), any())).thenReturn(FlowRunTransition.rejected(WAITING));
        when(stateService.failWaiting(eq(2L), any())).thenReturn(FlowRunTransition.applied(FAILURE));

        dispatcher.advanceWaitingRuns();

        verify(stateService).failWaiting(eq(1L), any());
        verify(stateService).failWaiting(eq(2L), any());
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
