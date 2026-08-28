package com.flink.platform.web.runner;

import com.flink.platform.alert.AlertSendingService;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.service.DependencyService;
import com.flink.platform.web.service.DependencyService.DependencyState;
import com.flink.platform.web.service.KillJobService;
import com.flink.platform.web.service.QuartzService;
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
import static com.flink.platform.common.enums.ExecutionStatus.WAITING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
        run.setScheduleTime(LocalDateTime.now());
        // next fire is strictly after scheduleTime (>= now), so not timed out yet
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
