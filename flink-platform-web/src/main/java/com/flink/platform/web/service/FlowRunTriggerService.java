package com.flink.platform.web.service;

import com.flink.platform.alert.AlertSendingService;
import com.flink.platform.dao.entity.ExecutionConfig;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.web.model.FlowRunCreationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** Handles a flow-run trigger: creates the run, then reports the outcome to logs and alerts. */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class FlowRunTriggerService {

    private final AlertSendingService alertSendingService;

    private final FlowRunCreationService creationService;

    public FlowRunCreationResult handle(JobFlow jobFlow, LocalDateTime scheduleTime) {
        return handle(jobFlow, null, null, scheduleTime);
    }

    public FlowRunCreationResult handle(
            JobFlow jobFlow, @Nullable ExecutionConfig config, @Nullable Long userId, LocalDateTime scheduleTime) {
        var creationResult = creationService.tryCreateFlowRun(jobFlow, config, userId, scheduleTime);
        report(creationResult, jobFlow);
        return creationResult;
    }

    private void report(FlowRunCreationResult result, JobFlow jobFlow) {
        result.timeouts().forEach(timeout -> {
            log.warn(
                    "{}, failed waiting flow run: {}",
                    timeout.reason(),
                    timeout.run().getId());
            alertSendingService.sendAlerts(timeout.run(), timeout.reason());
        });

        switch (result.state()) {
            case BLOCKED -> {
                log.warn("Job flow {} blocked: {}", jobFlow.getId(), result.blockMessage());
                alertSendingService.sendErrAlerts(jobFlow, result.blockMessage());
            }
            case CREATED -> log.info("Job flow run: {} is created", result.run().getId());
        }
    }
}
