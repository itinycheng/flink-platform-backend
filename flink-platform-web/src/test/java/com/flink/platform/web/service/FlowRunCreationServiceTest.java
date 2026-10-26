package com.flink.platform.web.service;

import com.flink.platform.dao.entity.ExecutionConfig;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.web.config.WorkerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.RUNNING;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static com.flink.platform.common.enums.ExecutionStatus.WAITING;
import static com.flink.platform.common.enums.JobFlowType.JOB_FLOW;
import static com.flink.platform.web.model.FlowRunCreationResult.State.BLOCKED;
import static com.flink.platform.web.model.FlowRunCreationResult.State.CREATED;
import static com.flink.platform.web.service.DependencyService.DependencyState.SATISFIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowRunCreationServiceTest {

    private static final LocalDateTime SCHEDULE_TIME = LocalDateTime.of(2026, 10, 27, 10, 0);

    @Mock
    private JobFlowService jobFlowService;

    @Mock
    private JobFlowRunService jobFlowRunService;

    @Mock
    private FlowScheduleService flowScheduleService;

    @Mock
    private DependencyService dependencyService;

    @Mock
    private LegacyJobListService legacyJobListService;

    private FlowRunCreationService service;

    private JobFlow flow;

    @BeforeEach
    void setUp() {
        var workerConfig = new WorkerConfig();
        workerConfig.setPerFlowExecThreads(5);
        workerConfig.setMaxPerFlowExecThreads(10);
        workerConfig.setMaxActiveRunsPerFlow(10);
        service = new FlowRunCreationService(
                jobFlowService,
                jobFlowRunService,
                new JobFlowRunBizService(jobFlowRunService, workerConfig),
                flowScheduleService,
                workerConfig,
                dependencyService,
                legacyJobListService);

        flow = new JobFlow();
        flow.setId(10L);
        flow.setName("child");
        flow.setCode("child-code");
        flow.setType(JOB_FLOW);
        flow.setUserId(20L);
        flow.setWorkspaceId(30L);
    }

    @Test
    void createsCompleteRunWithMergedOverrideAndInitialStatus() {
        var flowConfig = new ExecutionConfig();
        flowConfig.setMaxJobsPerRun(7);
        flow.setConfig(flowConfig);
        var override = new ExecutionConfig();
        override.setMaxActiveRuns(3);
        when(dependencyService.resolveAndEvaluate(null)).thenReturn(SATISFIED);
        when(jobFlowRunService.listActiveRuns(10L)).thenReturn(List.of());
        when(jobFlowRunService.save(any())).thenAnswer(invocation -> {
            JobFlowRun run = invocation.getArgument(0);
            run.setId(42L);
            return true;
        });

        var result = service.tryCreateFlowRun(flow, override, 21L, SCHEDULE_TIME);

        assertEquals(CREATED, result.state());
        assertEquals(42L, result.run().getId());
        assertEquals(SUBMITTED, result.run().getStatus());
        assertEquals(10L, result.run().getFlowId());
        assertEquals(21L, result.run().getUserId());
        assertEquals(30L, result.run().getWorkspaceId());
        assertEquals(SCHEDULE_TIME, result.run().getScheduleTime());
        assertEquals(7, result.run().getConfig().getMaxJobsPerRun());
        assertEquals(3, result.run().getConfig().getMaxActiveRuns());
    }

    @Test
    void activeRunAtSameScheduleBlocksCreation() {
        var active = new JobFlowRun();
        active.setId(41L);
        active.setStatus(RUNNING);
        active.setScheduleTime(SCHEDULE_TIME);
        active.setConfig(new ExecutionConfig());
        when(jobFlowRunService.listActiveRuns(10L)).thenReturn(List.of(active));

        var result = service.tryCreateFlowRun(flow, null, null, SCHEDULE_TIME);

        assertEquals(BLOCKED, result.state());
        assertSame(active, result.blockOn());
        verify(jobFlowRunService, never()).save(any());
    }

    @Test
    void expiredWaitingRunFailsBeforeNewRunIsCreated() {
        var expired = new JobFlowRun();
        expired.setId(41L);
        expired.setStatus(WAITING);
        ReflectionTestUtils.setField(expired, "createTime", SCHEDULE_TIME.minusHours(2));
        when(jobFlowRunService.listActiveRuns(10L)).thenReturn(List.of(expired));
        when(flowScheduleService.resolveWaitDeadline(expired)).thenReturn(SCHEDULE_TIME.minusHours(1));
        when(jobFlowRunService.updateStatusFromTo(41L, WAITING, FAILURE)).thenReturn(true);
        when(jobFlowRunService.save(any())).thenReturn(true);

        var result = service.tryCreateFlowRun(flow, null, null, SCHEDULE_TIME);

        assertEquals(CREATED, result.state());
        assertEquals(FAILURE, expired.getStatus());
        assertEquals(1, result.timeouts().size());
        assertSame(expired, result.timeouts().getFirst().run());
        assertTrue(result.timeouts().getFirst().reason().startsWith("Waiting timeout"));
        verify(jobFlowRunService).save(result.run());
    }
}
