package com.flink.platform.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.flink.platform.common.constants.Constant.HOST_IP;
import static com.flink.platform.common.enums.ExecutionStatus.getNonTerminals;

/** Init job flow scheduler when the project started. */
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class InitJobFlowScheduler {

    private final JobFlowRunService jobFlowRunService;

    private final JobFlowScheduleService jobFlowScheduleService;

    private final QuartzService quartzService;

    @PostConstruct
    public void init() {
        CompletableFuture.runAsync(() -> {
            quartzService.waitForStarted();
            appendOwnedJobFlowRunToScheduler();
        });
    }

    public void appendOwnedJobFlowRunToScheduler() {
        jobFlowRunService
                .list(new QueryWrapper<JobFlowRun>()
                        .lambda()
                        .eq(JobFlowRun::getHost, HOST_IP)
                        .in(JobFlowRun::getStatus, getNonTerminals()))
                .forEach(jobFlowScheduleService::registerToScheduler);
    }
}
