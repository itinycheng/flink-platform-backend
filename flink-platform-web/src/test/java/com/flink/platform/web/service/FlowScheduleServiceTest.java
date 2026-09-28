package com.flink.platform.web.service;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.dao.entity.ExecutionConfig;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.quartz.QuartzService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.RUNNING;
import static com.flink.platform.common.enums.ExecutionStatus.WAITING;
import static com.flink.platform.web.model.FlowRunCreationResult.State.BLOCKED;
import static com.flink.platform.web.model.FlowRunCreationResult.State.CREATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowScheduleServiceTest {

    private static final LocalDateTime FIRE_TIME = LocalDateTime.now().withNano(0);

    @Mock
    private JobFlowService jobFlowService;

    @Mock
    private JobFlowRunService jobFlowRunService;

    @Mock
    private WorkerConfig workerConfig;

    @Spy
    private QuartzService quartzService = new QuartzService();

    private FlowScheduleService flowScheduleService;

    @BeforeEach
    void setUp() {
        var flowRunBizService = new JobFlowRunBizService(jobFlowRunService, workerConfig);
        flowScheduleService = new FlowScheduleService(
                jobFlowService, jobFlowRunService, flowRunBizService, quartzService, workerConfig);
        lenient().when(jobFlowRunService.saveOrUpdate(any())).thenReturn(true);
        lenient().when(workerConfig.getMaxActiveRunsPerFlow()).thenReturn(10);
    }

    @Test
    void waitingRunExpiringAtFreshOccurrenceIsFailedAndFreshOccurrenceIsAccepted() {
        var oldRun = waitingRun(1L, FIRE_TIME.minusHours(2));
        var candidate = candidate(null);
        var executionConfig = new ExecutionConfig();
        candidate.setConfig(executionConfig);
        when(jobFlowRunService.listActiveRuns(10L, null)).thenReturn(List.of(oldRun), List.of());
        when(jobFlowRunService.updateStatusFromTo(1L, WAITING, FAILURE)).thenReturn(true);

        var result = flowScheduleService.tryCreateFlowRun(candidate);

        assertEquals(CREATED, result.state());
        assertNull(result.blockOn());
        assertEquals(1, result.timeouts().size());
        assertSame(oldRun, result.timeouts().getFirst().run());
        assertEquals(FAILURE, oldRun.getStatus());
        // The candidate status is decided by the caller, admission must not touch it.
        assertEquals(WAITING, candidate.getStatus());
        assertTrue(result.timeouts().getFirst().reason().startsWith("Waiting timeout, dependencies not satisfied by "));
        verify(jobFlowService).lockForUpdate(10L);
        verify(jobFlowRunService).saveOrUpdate(candidate);
    }

    @Test
    void terminalRunAtSameScheduleCanBeRetried() {
        var candidate = candidate(null);
        candidate.setConfig(new ExecutionConfig());
        // Terminal runs are absent from the active-run snapshot.
        when(jobFlowRunService.listActiveRuns(10L, null)).thenReturn(List.of());

        var result = flowScheduleService.tryCreateFlowRun(candidate);

        assertEquals(CREATED, result.state());
        assertNull(result.blockOn());
        verify(jobFlowRunService).saveOrUpdate(candidate);
    }

    @Test
    void waitingRunBeforeItsDeadlineStillBlocks() {
        var oldRun = waitingRun(1L, FIRE_TIME);
        oldRun.setCronExpr(null);
        var candidate = candidate(null);
        var executionConfig = new ExecutionConfig();
        candidate.setConfig(executionConfig);
        when(workerConfig.getFlowWaitTimeout()).thenReturn(Duration.ofDays(1));
        when(jobFlowRunService.listActiveRuns(10L, null)).thenReturn(List.of(oldRun));

        var result = flowScheduleService.tryCreateFlowRun(candidate);

        assertEquals(BLOCKED, result.state());
        assertSame(oldRun, result.blockOn());
        verify(jobFlowRunService, never()).updateStatusFromTo(1L, WAITING, FAILURE);
        verify(jobFlowRunService, never()).saveOrUpdate(candidate);
    }

    @Test
    void admissionUsesReadCommittedSnapshots() throws NoSuchMethodException {
        var method = FlowScheduleService.class.getDeclaredMethod("tryCreateFlowRun", JobFlowRun.class);
        var transactional = method.getAnnotation(Transactional.class);

        assertEquals(
                TransactionDefinition.ISOLATION_READ_COMMITTED,
                transactional.isolation().value());
    }

    @Test
    void futureLogicalScheduleDoesNotExpireWaitingRunBeforePhysicalDeadline() {
        var admissionTime = LocalDateTime.now();
        var waitStartedAt = admissionTime;
        var oldRun = waitingRun(1L, waitStartedAt);
        oldRun.setCronExpr(null);
        var candidate = candidate(null);
        candidate.setScheduleTime(waitStartedAt.plusYears(1));
        candidate.setConfig(new ExecutionConfig());
        when(workerConfig.getFlowWaitTimeout()).thenReturn(Duration.ofDays(1));
        when(jobFlowRunService.listActiveRuns(10L, null)).thenReturn(List.of(oldRun));

        var result = flowScheduleService.tryCreateFlowRun(candidate);

        assertEquals(BLOCKED, result.state());
        assertSame(oldRun, result.blockOn());
        verify(jobFlowRunService, never()).updateStatusFromTo(1L, WAITING, FAILURE);
        verify(jobFlowRunService, never()).saveOrUpdate(candidate);
    }

    @Test
    void historicalLogicalScheduleDoesNotPreserveRunPastPhysicalDeadline() {
        var admissionTime = LocalDateTime.now();
        var waitStartedAt = admissionTime.minusDays(2);
        var oldRun = waitingRun(1L, waitStartedAt);
        oldRun.setCronExpr(null);
        var candidate = candidate(null);
        candidate.setScheduleTime(waitStartedAt.minusYears(1));
        candidate.setConfig(new ExecutionConfig());
        when(workerConfig.getFlowWaitTimeout()).thenReturn(Duration.ofDays(1));
        when(jobFlowRunService.listActiveRuns(10L, null)).thenReturn(List.of(oldRun), List.of());
        when(jobFlowRunService.updateStatusFromTo(1L, WAITING, FAILURE)).thenReturn(true);

        var result = flowScheduleService.tryCreateFlowRun(candidate);

        assertEquals(CREATED, result.state());
        assertEquals(1, result.timeouts().size());
        assertSame(oldRun, result.timeouts().getFirst().run());
        verify(jobFlowRunService).saveOrUpdate(candidate);
    }

    @Test
    void concurrentWaitingStatusChangeFailsAdmissionWithoutCreatingCandidate() {
        var oldRun = waitingRun(1L, FIRE_TIME.minusHours(2));
        var candidate = candidate(null);
        var executionConfig = new ExecutionConfig();
        candidate.setConfig(executionConfig);
        when(jobFlowRunService.listActiveRuns(10L, null)).thenReturn(List.of(oldRun));
        when(jobFlowRunService.updateStatusFromTo(1L, WAITING, FAILURE)).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> flowScheduleService.tryCreateFlowRun(candidate));
        verify(jobFlowRunService, never()).saveOrUpdate(candidate);
    }

    @Test
    void runningFlowKeepsTheLegacyOverlapGuard() {
        var active = waitingRun(1L, FIRE_TIME.minusMinutes(1));
        active.setStatus(RUNNING);
        var candidate = candidate(null);
        var executionConfig = new ExecutionConfig();
        candidate.setConfig(executionConfig);
        when(jobFlowRunService.listActiveRuns(10L, null)).thenReturn(List.of(active));

        var result = flowScheduleService.tryCreateFlowRun(candidate);

        assertEquals(BLOCKED, result.state());
        assertSame(active, result.blockOn());
        verify(jobFlowRunService, never()).saveOrUpdate(candidate);
    }

    @Test
    void activeRunTakesPrecedenceOverExpiredWaitingRun() {
        var expired = waitingRun(1L, FIRE_TIME.minusHours(2));
        var active = waitingRun(2L, FIRE_TIME.minusMinutes(1));
        active.setStatus(RUNNING);
        var candidate = candidate(null);
        var executionConfig = new ExecutionConfig();
        candidate.setConfig(executionConfig);
        when(jobFlowRunService.listActiveRuns(10L, null)).thenReturn(List.of(expired, active));

        var result = flowScheduleService.tryCreateFlowRun(candidate);

        assertEquals(BLOCKED, result.state());
        assertSame(active, result.blockOn());
        verify(jobFlowRunService, never()).updateStatusFromTo(anyLong(), any(), any());
        verify(jobFlowRunService, never()).saveOrUpdate(candidate);
    }

    @Test
    void allExpiredWaitingRunsAreFailedBeforeFreshOccurrenceIsAccepted() {
        var first = waitingRun(1L, FIRE_TIME.minusHours(3));
        var second = waitingRun(2L, FIRE_TIME.minusHours(2));
        var candidate = candidate(null);
        var executionConfig = new ExecutionConfig();
        candidate.setConfig(executionConfig);
        when(jobFlowRunService.listActiveRuns(10L, null)).thenReturn(List.of(first, second), List.of());
        when(jobFlowRunService.updateStatusFromTo(1L, WAITING, FAILURE)).thenReturn(true);
        when(jobFlowRunService.updateStatusFromTo(2L, WAITING, FAILURE)).thenReturn(true);

        var result = flowScheduleService.tryCreateFlowRun(candidate);

        assertEquals(CREATED, result.state());
        assertEquals(2, result.timeouts().size());
        assertEquals(FAILURE, first.getStatus());
        assertEquals(FAILURE, second.getStatus());
        verify(jobFlowRunService).saveOrUpdate(candidate);
    }

    @Test
    void precreatedSubflowRunKeepsTheLegacySaveOrUpdatePath() {
        var candidate = candidate(7L);
        var executionConfig = new ExecutionConfig();
        candidate.setConfig(executionConfig);
        when(jobFlowRunService.listActiveRuns(10L, 7L)).thenReturn(List.of());

        var result = flowScheduleService.tryCreateFlowRun(candidate);

        assertEquals(CREATED, result.state());
        assertNull(result.blockOn());
        verify(jobFlowRunService).listActiveRuns(10L, 7L);
        verify(jobFlowRunService).saveOrUpdate(candidate);
    }

    @Test
    void blockedPrecreatedSubflowRunIsFailedInsteadOfLeftCreated() {
        var candidate = candidate(7L);
        var executionConfig = new ExecutionConfig();
        candidate.setConfig(executionConfig);
        var active = waitingRun(1L, FIRE_TIME.minusMinutes(1));
        active.setStatus(RUNNING);
        when(jobFlowRunService.listActiveRuns(10L, 7L)).thenReturn(List.of(active));
        when(jobFlowRunService.updateStatusFromTo(7L, ExecutionStatus.CREATED, FAILURE))
                .thenReturn(true);

        var result = flowScheduleService.tryCreateFlowRun(candidate);

        assertEquals(BLOCKED, result.state());
        assertSame(active, result.blockOn());
        verify(jobFlowRunService).updateStatusFromTo(7L, ExecutionStatus.CREATED, FAILURE);
        verify(jobFlowRunService, never()).saveOrUpdate(candidate);
    }

    @Test
    void failedSaveRollsBackCreation() {
        var candidate = candidate(null);
        candidate.setConfig(new ExecutionConfig());
        when(jobFlowRunService.saveOrUpdate(candidate)).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> flowScheduleService.tryCreateFlowRun(candidate));
    }

    private JobFlowRun candidate(Long id) {
        var run = new JobFlowRun();
        run.setId(id);
        run.setFlowId(10L);
        run.setScheduleTime(FIRE_TIME);
        run.setStatus(WAITING);
        return run;
    }

    private JobFlowRun waitingRun(Long id, LocalDateTime scheduleTime) {
        var run = new JobFlowRun();
        run.setId(id);
        run.setFlowId(10L);
        run.setStatus(WAITING);
        run.setCronExpr("0 * * * * ?");
        run.setScheduleTime(scheduleTime);
        ReflectionTestUtils.setField(run, "createTime", scheduleTime);
        return run;
    }
}
