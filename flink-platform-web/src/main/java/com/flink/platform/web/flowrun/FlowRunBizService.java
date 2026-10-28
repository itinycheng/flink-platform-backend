package com.flink.platform.web.flowrun;

import com.flink.platform.common.enums.ExecutionStrategy;
import com.flink.platform.dao.entity.ExecutionConfig;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.quartz.QuartzService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static com.flink.platform.web.flowrun.FlowRunBizService.ConflictType.DUPLICATE_SCHEDULE;
import static com.flink.platform.web.flowrun.FlowRunBizService.ConflictType.DUPLICATE_START_JOB;
import static com.flink.platform.web.flowrun.FlowRunBizService.ConflictType.MAX_ACTIVE_RUNS;
import static java.time.temporal.ChronoUnit.SECONDS;

/** Business rules for admitting a new flow run. */
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class FlowRunBizService {

    private final JobFlowRunService flowRunService;

    private final QuartzService quartzService;

    private final WorkerConfig workerConfig;

    // TODO: A run created near the next cron fire can receive an impractically short wait deadline.
    public LocalDateTime resolveWaitDeadline(JobFlowRun jobFlowRun) {
        var createTime = jobFlowRun.getCreateTime();
        var cronExpr = jobFlowRun.getCronExpr();
        if (StringUtils.isBlank(cronExpr)) {
            return createTime.plus(workerConfig.getFlowWaitTimeout());
        }

        // A normal run must not wait beyond its next scheduled occurrence.
        var scheduleTime = jobFlowRun.getScheduleTime();
        var scheduledDeadline = quartzService.nextTriggerTimeAfter(cronExpr, scheduleTime);
        if (scheduledDeadline.isPresent() && scheduledDeadline.get().isAfter(createTime)) {
            return scheduledDeadline.get();
        }

        // A historical or severely delayed run waits until the next live scheduled occurrence.
        return quartzService
                .nextTriggerTimeAfter(cronExpr, createTime)
                .orElseGet(() -> createTime.plus(workerConfig.getFlowWaitTimeout()));
    }

    public @Nullable RunConflict findRunConflict(
            Long flowId, ExecutionConfig config, @Nullable LocalDateTime scheduleTime) {
        var activeRuns = flowRunService.listActiveRuns(flowId);
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
