package com.flink.platform.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobRunInfoService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.flink.platform.common.constants.Constant.HOST_IP;
import static com.flink.platform.common.enums.ExecutionStatus.getNonTerminals;

/** Init job flow scheduler when the project started. */
@Component
public class InitJobFlowScheduler {

    @Autowired
    private JobFlowRunService jobFlowRunService;

    @Autowired
    private JobFlowScheduleService jobFlowScheduleService;

    @Autowired
    private JobRunInfoService jobRunInfoService;

    @Autowired
    private QuartzService quartzService;

    @Autowired
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
