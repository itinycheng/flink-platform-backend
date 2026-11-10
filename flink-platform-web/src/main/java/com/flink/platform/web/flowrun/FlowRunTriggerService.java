package com.flink.platform.web.flowrun;

import com.flink.platform.alert.AlertSendingService;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.dao.entity.ExecutionConfig;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.service.DependencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static com.flink.platform.common.enums.ExecutionStatus.WAITING;
import static com.flink.platform.web.service.DependencyService.DependencyState.SATISFIED;

/** Handles a flow-run trigger: creates the run, then reports the outcome to logs and alerts. */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class FlowRunTriggerService {

    private final WorkerConfig workerConfig;

    private final DependencyService dependencyService;

    private final AlertSendingService alertSendingService;

    private final FlowRunCreationService creationService;

    public FlowRunCreationResult handle(JobFlow jobFlow, LocalDateTime scheduleTime) {
        return handle(jobFlow, null, null, scheduleTime);
    }

    public FlowRunCreationResult handle(
            JobFlow jobFlow, @Nullable ExecutionConfig config, @Nullable Long userId, LocalDateTime scheduleTime) {
        var candidate = buildRun(jobFlow, config, userId, scheduleTime);
        var creationResult = creationService.tryCreateFlowRun(candidate);
        report(creationResult, jobFlow);
        return creationResult;
    }

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

    private void report(FlowRunCreationResult result, JobFlow jobFlow) {
        result.timeouts()
                .forEach(timeout -> log.warn(
                        "{}, failed waiting flow run: {}",
                        timeout.reason(),
                        timeout.run().getId()));

        switch (result.state()) {
            case BLOCKED -> {
                log.warn("Job flow {} blocked: {}", jobFlow.getId(), result.blockMessage());
                alertSendingService.sendErrAlerts(jobFlow, result.blockMessage());
            }
            case CREATED -> log.info("Job flow run: {} is created", result.run().getId());
        }
    }
}
