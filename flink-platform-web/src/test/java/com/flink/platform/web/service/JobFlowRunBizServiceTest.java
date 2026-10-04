package com.flink.platform.web.service;

import com.flink.platform.common.enums.ExecutionStrategy;
import com.flink.platform.dao.entity.ExecutionConfig;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.web.config.WorkerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static com.flink.platform.common.enums.ExecutionStrategy.ALL_POST_JOBS;
import static com.flink.platform.common.enums.ExecutionStrategy.ALL_PRE_JOBS;
import static com.flink.platform.common.enums.ExecutionStrategy.ONLY_CUR_JOB;
import static com.flink.platform.web.service.JobFlowRunBizService.ConflictType.DUPLICATE_SCHEDULE;
import static com.flink.platform.web.service.JobFlowRunBizService.ConflictType.DUPLICATE_START_JOB;
import static com.flink.platform.web.service.JobFlowRunBizService.ConflictType.MAX_ACTIVE_RUNS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class JobFlowRunBizServiceTest {

    private static final long FLOW_ID = 1L;
    private static final LocalDateTime SCHEDULE_TIME = LocalDateTime.of(2026, 10, 5, 10, 0);

    private StubJobFlowRunService flowRunService;

    private WorkerConfig workerConfig;

    private JobFlowRunBizService service;

    @BeforeEach
    void setUp() {
        flowRunService = new StubJobFlowRunService();
        workerConfig = new WorkerConfig();
        workerConfig.setMaxActiveRunsPerFlow(10);
        service = new JobFlowRunBizService(flowRunService, workerConfig);
    }

    @Test
    void startJobStrategiesRejectSameStrategyAndStartJobRegardlessOfSchedule() {
        for (var strategy : List.of(ONLY_CUR_JOB, ALL_POST_JOBS, ALL_PRE_JOBS)) {
            var activeRun = run(11L, manualConfig(strategy, 100L, SCHEDULE_TIME.minusDays(1), 10));
            flowRunService.activeRuns = List.of(activeRun);

            var conflict = service.findRunConflict(FLOW_ID, manualConfig(strategy, 100L, SCHEDULE_TIME, 10), null);

            assertEquals(DUPLICATE_START_JOB, conflict.type(), strategy.name());
            assertSame(activeRun, conflict.activeRun(), strategy.name());
        }
    }

    @Test
    void startJobStrategyAllowsAnotherStartJobWhenCapacityIsAvailable() {
        flowRunService.activeRuns = List.of(run(11L, manualConfig(ONLY_CUR_JOB, 200L, SCHEDULE_TIME, 2)));

        var conflict = service.findRunConflict(FLOW_ID, manualConfig(ONLY_CUR_JOB, 100L, SCHEDULE_TIME, 2), null);

        assertNull(conflict);
    }

    @Test
    void startJobStrategyAllowsAnotherStrategyWhenCapacityIsAvailable() {
        flowRunService.activeRuns = List.of(run(11L, manualConfig(ALL_POST_JOBS, 100L, SCHEDULE_TIME, 2)));

        var conflict = service.findRunConflict(FLOW_ID, manualConfig(ONLY_CUR_JOB, 100L, SCHEDULE_TIME, 2), null);

        assertNull(conflict);
    }

    @Test
    void scheduledRunRejectsSameScheduleAtSecondPrecision() {
        var activeRun = run(11L, scheduledConfig(SCHEDULE_TIME.plusNanos(999_000_000), 2));
        flowRunService.activeRuns = List.of(activeRun);

        var conflict = service.findRunConflict(FLOW_ID, scheduledConfig(SCHEDULE_TIME, 2), null);

        assertEquals(DUPLICATE_SCHEDULE, conflict.type());
        assertSame(activeRun, conflict.activeRun());
    }

    @Test
    void manualAndScheduledRunsUseSeparateDuplicateRules() {
        flowRunService.activeRuns = List.of(run(11L, manualConfig(ONLY_CUR_JOB, 100L, SCHEDULE_TIME, 2)));

        assertNull(service.findRunConflict(FLOW_ID, scheduledConfig(SCHEDULE_TIME, 2), null));

        flowRunService.activeRuns = List.of(run(12L, scheduledConfig(SCHEDULE_TIME, 2)));

        assertNull(service.findRunConflict(FLOW_ID, manualConfig(ONLY_CUR_JOB, 100L, SCHEDULE_TIME, 2), null));
    }

    @Test
    void maxActiveRunsCountsAllRunsInTheFlow() {
        var activeRun = run(11L, manualConfig(ONLY_CUR_JOB, 200L, SCHEDULE_TIME, 1));
        flowRunService.activeRuns = List.of(activeRun);

        var conflict = service.findRunConflict(FLOW_ID, manualConfig(ONLY_CUR_JOB, 100L, SCHEDULE_TIME, 1), null);

        assertEquals(MAX_ACTIVE_RUNS, conflict.type());
        assertSame(activeRun, conflict.activeRun());
    }

    @Test
    void platformLimitCapsMaxActiveRunsAtRuntime() {
        workerConfig.setMaxActiveRunsPerFlow(2);
        flowRunService.activeRuns = List.of(
                run(11L, scheduledConfig(SCHEDULE_TIME.minusDays(1), 10)),
                run(12L, scheduledConfig(SCHEDULE_TIME.minusDays(2), 10)));

        var conflict = service.findRunConflict(FLOW_ID, scheduledConfig(SCHEDULE_TIME, 10), null);

        assertEquals(MAX_ACTIVE_RUNS, conflict.type());
    }

    @Test
    void forwardsExcludedRunIdToActiveRunQuery() {
        assertNull(service.findRunConflict(FLOW_ID, scheduledConfig(SCHEDULE_TIME, 1), 99L));
        assertEquals(FLOW_ID, flowRunService.queriedFlowId);
        assertEquals(99L, flowRunService.excludedRunId);
    }

    @Test
    void conflictTypesHaveUserFacingDescriptions() {
        assertEquals("Duplicate execution strategy and start job", DUPLICATE_START_JOB.getDescription());
        assertEquals("Duplicate schedule time", DUPLICATE_SCHEDULE.getDescription());
        assertEquals("Maximum active runs reached", MAX_ACTIVE_RUNS.getDescription());
    }

    private static ExecutionConfig scheduledConfig(LocalDateTime scheduleTime, int maxActiveRuns) {
        var config = new ExecutionConfig();
        config.setScheduleTime(scheduleTime);
        config.setMaxActiveRuns(maxActiveRuns);
        return config;
    }

    private static ExecutionConfig manualConfig(
            ExecutionStrategy strategy, Long startJobId, LocalDateTime scheduleTime, int maxActiveRuns) {
        var config = scheduledConfig(scheduleTime, maxActiveRuns);
        config.setStrategy(strategy);
        config.setStartJobId(startJobId);
        return config;
    }

    private static JobFlowRun run(Long id, ExecutionConfig config) {
        var run = new JobFlowRun();
        run.setId(id);
        run.setScheduleTime(config.getScheduleTime());
        run.setConfig(config);
        return run;
    }

    private static final class StubJobFlowRunService extends JobFlowRunService {

        private List<JobFlowRun> activeRuns = List.of();
        private Long queriedFlowId;
        private Long excludedRunId;

        @Override
        public List<JobFlowRun> listActiveRuns(Long flowId, Long excludedRunId) {
            queriedFlowId = flowId;
            this.excludedRunId = excludedRunId;
            return activeRuns;
        }
    }
}
