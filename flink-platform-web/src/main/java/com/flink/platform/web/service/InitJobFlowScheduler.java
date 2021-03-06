package com.flink.platform.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.graph.DAG;
import com.flink.platform.common.model.JobEdge;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobRunInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.flink.platform.common.enums.ExecutionStatus.getNonTerminals;

/** Init job flow scheduler when the project started. */
@Component
public class InitJobFlowScheduler {

    @Autowired private JobFlowRunService jobFlowRunService;

    @Autowired private JobFlowScheduleService jobFlowScheduleService;

    @Autowired private JobRunInfoService jobRunInfoService;

    @Autowired private QuartzService quartzService;

    @Autowired
    @PostConstruct
    public void init() {
        CompletableFuture.runAsync(
                () -> {
                    quartzService.waitForStarted();
                    appendExistedJobFlowRunToScheduler();
                });
    }

    public void appendExistedJobFlowRunToScheduler() {
        List<JobFlowRun> unfinishedFlowRunList =
                jobFlowRunService.list(
                        new QueryWrapper<JobFlowRun>()
                                .lambda()
                                .eq(JobFlowRun::getHost, Constant.HOST_IP)
                                .in(JobFlowRun::getStatus, getNonTerminals()));

        for (JobFlowRun jobFlowRun : unfinishedFlowRunList) {
            DAG<Long, JobVertex, JobEdge> flow = jobFlowRun.getFlow();

            // Update status of JobVertex in flow.
            jobRunInfoService
                    .list(
                            new QueryWrapper<JobRunInfo>()
                                    .lambda()
                                    .eq(JobRunInfo::getFlowRunId, jobFlowRun.getId()))
                    .forEach(
                            jobRunInfo -> {
                                JobVertex vertex = flow.getVertex(jobRunInfo.getJobId());
                                vertex.setJobRunId(jobRunInfo.getId());
                                vertex.setJobRunStatus(jobRunInfo.getStatus());
                            });

            jobFlowScheduleService.registerToScheduler(jobFlowRun);
        }
    }
}
