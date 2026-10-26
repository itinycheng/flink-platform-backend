package com.flink.platform.web.service;

import com.flink.platform.common.constants.Constant;
import com.flink.platform.dao.entity.ExecutionConfig;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.model.FlowRunCreationResult;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;

import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static com.flink.platform.common.enums.ExecutionStatus.WAITING;
import static com.flink.platform.common.enums.JobFlowType.JOB_LIST;
import static com.flink.platform.web.service.DependencyService.DependencyState.SATISFIED;

@NullMarked
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class FlowRunCreationService {

    private final JobFlowService jobFlowService;

    private final JobFlowRunService jobFlowRunService;

    private final JobFlowRunBizService flowRunBizService;

    private final FlowScheduleService flowScheduleService;

    private final WorkerConfig workerConfig;

    private final DependencyService dependencyService;

    private final LegacyJobListService legacyJobListService;

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public FlowRunCreationResult tryCreateFlowRun(
            JobFlow jobFlow, @Nullable ExecutionConfig config, @Nullable Long userId, LocalDateTime scheduleTime) {
        var candidate = buildRun(jobFlow, config, userId, scheduleTime);
        jobFlowService.lockForUpdate(candidate.getFlowId());

        // TODO: legacy, remove in the future.
        if (JOB_LIST.equals(candidate.getType())) {
            return legacyJobListService.create(candidate);
        }

        var timeouts = new ArrayList<FlowRunCreationResult.WaitTimeout>();
        var activeRuns = jobFlowRunService.listActiveRuns(candidate.getFlowId());
        var expiredRuns = activeRuns.stream()
                .filter(run -> run.getStatus() == WAITING)
                .filter(run -> !flowScheduleService.resolveWaitDeadline(run).isAfter(LocalDateTime.now()))
                .toList();

        var remainingRuns =
                activeRuns.stream().filter(run -> !expiredRuns.contains(run)).toList();
        var conflict =
                flowRunBizService.findRunConflict(candidate.getConfig(), candidate.getScheduleTime(), remainingRuns);
        if (conflict != null) {
            return FlowRunCreationResult.blocked(
                    candidate, conflict.activeRun(), conflict.type().getDescription(), timeouts);
        }

        for (var waitingRun : expiredRuns) {
            // The dispatcher may have advanced or failed this run after the query.
            if (!jobFlowRunService.updateStatusFromTo(waitingRun.getId(), WAITING, FAILURE)) {
                throw new IllegalStateException(
                        "Waiting flow run status changed during admission: " + waitingRun.getId());
            }

            waitingRun.setStatus(FAILURE);
            waitingRun.setEndTime(LocalDateTime.now());
            var deadline = flowScheduleService.resolveWaitDeadline(waitingRun);
            timeouts.add(new FlowRunCreationResult.WaitTimeout(
                    waitingRun, "Waiting timeout, dependencies not satisfied by " + deadline));
        }

        if (!jobFlowRunService.save(candidate)) {
            throw new IllegalStateException("Failed to save flow run: " + candidate.getId());
        }

        return FlowRunCreationResult.created(candidate, timeouts);
    }

    // ====================================================
    // ====================== private =====================
    // ====================================================

    private JobFlowRun buildRun(
            JobFlow jobFlow, @Nullable ExecutionConfig config, @Nullable Long userId, LocalDateTime scheduleTime) {
        var jobFlowRun = new JobFlowRun();
        jobFlowRun.setFlowId(jobFlow.getId());
        jobFlowRun.setName(
                String.join("-", jobFlow.getName(), jobFlow.getCode(), String.valueOf(System.currentTimeMillis())));
        jobFlowRun.setFlow(jobFlow.getFlow());
        jobFlowRun.setUserId(userId != null ? userId : jobFlow.getUserId());
        jobFlowRun.setWorkspaceId(jobFlow.getWorkspaceId());
        jobFlowRun.setHost(Constant.HOST_IP);
        jobFlowRun.setType(jobFlow.getType());
        jobFlowRun.setCronExpr(jobFlow.getCronExpr());
        jobFlowRun.setPriority(jobFlow.getPriority());
        jobFlowRun.setConfig(mergeExecutionConfig(jobFlow.getConfig(), config));
        jobFlowRun.setTags(jobFlow.getTags());
        jobFlowRun.setAlerts(jobFlow.getAlerts());
        jobFlowRun.setTimeout(jobFlow.getTimeout());
        jobFlowRun.setParams(jobFlow.getParams());
        jobFlowRun.setScheduleTime(scheduleTime);
        // dependsOn
        var dependsOn = jobFlow.getDependsOn();
        jobFlowRun.setDependsOn(dependsOn);
        jobFlowRun.setStatus(dependencyService.resolveAndEvaluate(dependsOn) == SATISFIED ? SUBMITTED : WAITING);
        return jobFlowRun;
    }

    private ExecutionConfig mergeExecutionConfig(
            @Nullable ExecutionConfig flowConfig, @Nullable ExecutionConfig override) {
        var executionConfig = new ExecutionConfig();
        executionConfig.setMaxJobsPerRun(workerConfig.getPerFlowExecThreads());
        executionConfig.setMaxActiveRuns(1);
        executionConfig.mergeFrom(flowConfig);
        executionConfig.mergeFrom(override);
        executionConfig.setMaxJobsPerRun(
                Math.min(executionConfig.getMaxJobsPerRun(), workerConfig.getMaxPerFlowExecThreads()));
        return executionConfig;
    }
}
