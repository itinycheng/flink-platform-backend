package com.flink.platform.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.graph.DAG;
import com.flink.platform.common.model.JobEdge;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.dao.entity.JobFlowDag;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.config.AppRunner;
import com.flink.platform.web.config.WorkerConfig;
import com.flink.platform.web.runner.FlowExecuteThread;
import com.flink.platform.web.util.ThreadUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;

/** Schedule job flow. */
@Slf4j
@Component
public class JobFlowScheduleService {

    private final WorkerConfig workerConfig;

    private final JobRunInfoService jobRunInfoService;

    private final JobFlowRunService jobFlowRunService;

    private final AlertSendingService alertSendingService;

    private final ThreadPoolExecutor flowExecService;

    private final PriorityBlockingQueue<JobFlowRun> inFlightFlows;

    @Autowired
    public JobFlowScheduleService(
            WorkerConfig workerConfig,
            JobRunInfoService jobRunInfoService,
            JobFlowRunService jobFlowRunService,
            AlertSendingService alertSendingService) {
        this.workerConfig = workerConfig;
        this.jobRunInfoService = jobRunInfoService;
        this.jobFlowRunService = jobFlowRunService;
        this.alertSendingService = alertSendingService;
        this.flowExecService =
                ThreadUtil.newFixedThreadExecutor(
                        "FlowExecThread", workerConfig.getFlowExecThreads());
        this.inFlightFlows =
                new PriorityBlockingQueue<>(
                        workerConfig.getFlowExecThreads(),
                        (o1, o2) -> ObjectUtils.compare(o2.getPriority(), o1.getPriority()));
    }

    @Scheduled(fixedDelay = 1000)
    public void scheduleJobFlow() {
        while (AppRunner.isRunning()) {
            // TODO: activeCount is an approximate value.
            if (flowExecService.getActiveCount() > workerConfig.getFlowExecThreads()) {
                log.info(
                        "No enough threads to start a new job flow, active count: {}",
                        flowExecService.getActiveCount());
                return;
            }

            JobFlowRun jobFlowRun = inFlightFlows.poll();
            if (jobFlowRun == null) {
                return;
            }

            flowExecService.execute(new FlowExecuteThread(jobFlowRun, workerConfig));
        }
    }

    public void rebuildAndSchedule(JobFlowRun jobFlowRun) {
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

        registerToScheduler(jobFlowRun);
    }

    public synchronized void registerToScheduler(JobFlowRun jobFlowRun) {
        if (inFlightFlows.stream()
                .anyMatch(inQueue -> inQueue.getId().equals(jobFlowRun.getId()))) {
            log.warn("The JobFlowRun already registered, jobFlowRun: {} ", jobFlowRun);
            return;
        }

        JobFlowDag flow = jobFlowRun.getFlow();
        if (flow == null || CollectionUtils.isEmpty(flow.getVertices())) {
            log.warn(
                    "No JobVertex found, no scheduling required, flow run id: {}",
                    jobFlowRun.getId());
            JobFlowRun newJobFlowRun = new JobFlowRun();
            newJobFlowRun.setId(jobFlowRun.getId());
            newJobFlowRun.setStatus(FAILURE);
            newJobFlowRun.setEndTime(LocalDateTime.now());
            jobFlowRunService.updateById(newJobFlowRun);

            jobFlowRun.setStatus(FAILURE);
            jobFlowRun.setEndTime(LocalDateTime.now());
            alertSendingService.sendAlerts(jobFlowRun);
            return;
        }

        if (inFlightFlows.size() > 10 * workerConfig.getFlowExecThreads()) {
            log.warn("Not have enough resources to execute flow: {}", jobFlowRun);
            JobFlowRun newJobFlowRun = new JobFlowRun();
            newJobFlowRun.setId(jobFlowRun.getId());
            newJobFlowRun.setStatus(FAILURE);
            newJobFlowRun.setEndTime(LocalDateTime.now());
            jobFlowRunService.updateById(newJobFlowRun);

            jobFlowRun.setStatus(FAILURE);
            jobFlowRun.setEndTime(LocalDateTime.now());
            alertSendingService.sendAlerts(jobFlowRun);
            return;
        }

        inFlightFlows.offer(jobFlowRun);
    }
}
