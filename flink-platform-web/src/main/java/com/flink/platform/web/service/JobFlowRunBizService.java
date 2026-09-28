package com.flink.platform.web.service;

import com.flink.platform.common.enums.ExecutionStrategy;
import com.flink.platform.dao.entity.ExecutionConfig;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.web.config.WorkerConfig;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static com.flink.platform.web.service.JobFlowRunBizService.ConflictType.DUPLICATE_SCHEDULE;
import static com.flink.platform.web.service.JobFlowRunBizService.ConflictType.DUPLICATE_START_JOB;
import static com.flink.platform.web.service.JobFlowRunBizService.ConflictType.MAX_ACTIVE_RUNS;
import static java.time.temporal.ChronoUnit.SECONDS;

/** Business rules for admitting a new flow run. */
@NullMarked
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class JobFlowRunBizService {

    private final JobFlowRunService flowRunService;

    private final WorkerConfig workerConfig;

    public @Nullable RunConflict findRunConflict(
            Long flowId, ExecutionConfig config, @Nullable LocalDateTime scheduleTime) {
        return this.findRunConflict(flowId, config, scheduleTime, null);
    }

    public @Nullable RunConflict findRunConflict(
            Long flowId, ExecutionConfig config, @Nullable LocalDateTime scheduleTime, @Nullable Long excludedRunId) {
        var activeRuns = flowRunService.listActiveRuns(flowId, excludedRunId);
        return findRunConflict(config, scheduleTime, activeRuns);
    }

    /** Evaluates an already-locked active-run snapshot without issuing another database query. */
    public @Nullable RunConflict findRunConflict(
            ExecutionConfig config, @Nullable LocalDateTime scheduleTime, List<JobFlowRun> activeRuns) {
        var strategy = config.getStrategy();
        var startJobId = config.getStartJobId();
        if (scheduleTime != null) {
            scheduleTime = scheduleTime.truncatedTo(SECONDS);
        }

        for (var activeRun : activeRuns) {
            var activeConfig = activeRun.getConfig();
            ExecutionStrategy activeStrategy = null;
            Long activeStartJobId = null;
            if (activeConfig != null) {
                activeStrategy = activeConfig.getStrategy();
                activeStartJobId = activeConfig.getStartJobId();
            }

            if (ExecutionStrategy.needsStartJob(strategy)
                    && Objects.equals(strategy, activeStrategy)
                    && Objects.equals(startJobId, activeStartJobId)) {
                return new RunConflict(activeRun, DUPLICATE_START_JOB);
            }

            var activeScheduleTime = activeRun.getScheduleTime();
            if (!ExecutionStrategy.needsStartJob(strategy)
                    && !ExecutionStrategy.needsStartJob(activeStrategy)
                    && scheduleTime != null
                    && activeScheduleTime != null
                    && scheduleTime.equals(activeScheduleTime.truncatedTo(SECONDS))) {
                return new RunConflict(activeRun, DUPLICATE_SCHEDULE);
            }
        }

        var maxActiveRuns = Math.min(config.defaultOrGetMaxActiveRuns(), workerConfig.getMaxActiveRunsPerFlow());
        if (activeRuns.size() >= maxActiveRuns) {
            return new RunConflict(activeRuns.getFirst(), MAX_ACTIVE_RUNS);
        }

        return null;
    }

    @Getter
    public enum ConflictType {
        DUPLICATE_START_JOB("Duplicate execution strategy and start job"),
        DUPLICATE_SCHEDULE("Duplicate schedule time"),
        MAX_ACTIVE_RUNS("Maximum active runs reached");

        private final String description;

        ConflictType(String description) {
            this.description = description;
        }
    }

    public record RunConflict(JobFlowRun activeRun, ConflictType type) {}
}
