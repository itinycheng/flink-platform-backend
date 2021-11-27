package com.flink.platform.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.enums.ExecutionStatus;
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

import static com.flink.platform.common.enums.ExecutionStatus.getNonTerminals;

/** Init job flow scheduler when the project started. */
@Component
public class InitJobFlowScheduler {

    @Autowired private JobFlowRunService jobFlowRunService;

    @Autowired private JobFlowScheduleService jobFlowScheduleService;

    @Autowired private JobRunInfoService jobRunInfoService;

    @Autowired
    @PostConstruct
    public void init() {
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
                                vertex.setSubmitTime(jobRunInfo.getCreateTime());
                                vertex.setJobRunId(jobRunInfo.getId());
                                vertex.setJobRunStatus(
                                        ExecutionStatus.from(jobRunInfo.getStatus()));
                            });

            // TODO 重启有些已经调度但并未入库的没办法 生成process time
            jobFlowScheduleService.registerToScheduler(jobFlowRun);
        }
    }
}
