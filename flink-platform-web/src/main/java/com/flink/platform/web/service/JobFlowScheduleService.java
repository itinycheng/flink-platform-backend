package com.flink.platform.web.service;

import com.flink.platform.dao.entity.JobFlowDag;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.service.JobFlowRunService;
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

    private final JobFlowRunService jobFlowRunService;

    private final ThreadPoolExecutor flowExecService;

    private final PriorityBlockingQueue<JobFlowRun> inFlightFlows;

    @Autowired
    public JobFlowScheduleService(WorkerConfig workerConfig, JobFlowRunService jobFlowRunService) {
        this.workerConfig = workerConfig;
        this.jobFlowRunService = jobFlowRunService;
        this.flowExecService =
                ThreadUtil.newFixedThreadExecutor(
                        "FlowExecThread", workerConfig.getFlowExecThreads());
        this.inFlightFlows =
                new PriorityBlockingQueue<>(
                        workerConfig.getFlowExecThreads(),
                        (o1, o2) -> ObjectUtils.compare(o2.getPriority(), o1.getPriority()));
    }

    @Scheduled(fixedDelay = 2000)
    public void scheduleJobFlow() {
        // TODO: activeCount is an approximate value.
        if (flowExecService.getActiveCount() > workerConfig.getFlowExecThreads()) {
            return;
        }

        while (!inFlightFlows.isEmpty()) {
            JobFlowRun jobFlowRun = inFlightFlows.poll();
            if (jobFlowRun != null) {
                flowExecService.execute(new FlowExecuteThread(jobFlowRun, workerConfig));
            }
        }
    }

    public synchronized void registerToScheduler(JobFlowRun jobFlowRun) {
        JobFlowDag flow = jobFlowRun.getFlow();
        if (flow == null || CollectionUtils.isEmpty(flow.getVertices())) {
            log.warn(
                    "No JobVertex found, no scheduling required, flow run id: {}",
                    jobFlowRun.getId());
            return;
        }

        if (inFlightFlows.stream()
                .anyMatch(inQueue -> inQueue.getId().equals(jobFlowRun.getId()))) {
            log.warn("The JobFlowRun already registered, jobFlowRun: {} ", jobFlowRun);
            return;
        }

        if (inFlightFlows.size() > 2 * workerConfig.getFlowExecThreads()) {
            log.warn("Not have enough resources to execute flow: {}", jobFlowRun);
            JobFlowRun newJobFlowRun = new JobFlowRun();
            newJobFlowRun.setId(jobFlowRun.getId());
            newJobFlowRun.setStatus(FAILURE);
            newJobFlowRun.setEndTime(LocalDateTime.now());
            jobFlowRunService.updateById(newJobFlowRun);
            return;
        }

        inFlightFlows.offer(jobFlowRun);
    }
}
