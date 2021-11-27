package com.flink.platform.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.graph.DAG;
import com.flink.platform.common.model.JobEdge;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.common.util.Preconditions;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.JobRunInfo;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.JobInfoService;
import com.flink.platform.dao.service.JobRunInfoService;
import com.flink.platform.web.entity.JobQuartzInfo;
import com.flink.platform.web.util.JobFlowDagHelper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.flink.platform.common.enums.ExecutionStatus.RUNNING;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static com.flink.platform.web.entity.JobQuartzInfo.FLOW_RUN_ID;
import static java.util.stream.Collectors.toList;

/** schedule job flow. */
@Slf4j
@Service
public class JobFlowScheduleService {

    private static final Map<String, FlowContainer> IN_FLIGHT_FLOWS =
            Collections.synchronizedMap(
                    new TreeMap<String, FlowContainer>(Comparator.reverseOrder()));

    private static final ExecutorService EXECUTOR =
            new ThreadPoolExecutor(
                    20,
                    20,
                    0,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(),
                    Executors.defaultThreadFactory());

    @Autowired private QuartzService quartzService;

    @Autowired private JobInfoService jobInfoService;

    @Autowired private JobRunInfoService jobRunInfoService;

    @Autowired private JobFlowRunService jobFlowRunService;

    @Scheduled(cron = "0 0/10 * * * ?")
    public void schedule() {
        for (FlowContainer container : IN_FLIGHT_FLOWS.values()) {
            JobFlowRun jobFlowRun = container.getJobFlowRun();
            switch (container.getStatus()) {
                case SUBMITTED:
                case RUNNING:
                    EXECUTOR.execute(new JobFlowExecuteThread(container));
                    break;
                case SUCCEEDED:
                case KILLED:
                case FAILED:
                    log.warn(
                            "Job flow run: {} is already in terminal status: {}",
                            jobFlowRun.getId(),
                            jobFlowRun.getStatus());
                    break;
                default:
                    log.error(
                            "Job flow run :{} is in unexpected status {}",
                            jobFlowRun.getId(),
                            jobFlowRun.getStatus());
                    break;
            }
        }
    }

    public void registerToScheduler(JobFlowRun jobFlowRun) {
        DAG<Long, JobVertex, JobEdge> flow = jobFlowRun.getFlow();
        if (flow == null || CollectionUtils.isEmpty(flow.getVertices())) {
            log.warn(
                    "No JobVertex found, no scheduling required, flow run id: {}",
                    jobFlowRun.getId());
            return;
        }

        String flowKey =
                String.join(
                        "-", jobFlowRun.getPriority().toString(), jobFlowRun.getId().toString());
        FlowContainer container = new FlowContainer(flowKey, jobFlowRun);
        container.setStatus(SUBMITTED);
        container.setSubmitTime(LocalDateTime.now());
        IN_FLIGHT_FLOWS.put(flowKey, container);
    }

    @Transactional(rollbackFor = Exception.class)
    public void processFlow(FlowContainer container) {
        // TODO step 0: update interval check, handle begin vertices, use statusChanged.
        // step 1: update status in flow.
        updateJobVertexStatus(container);

        // step 2: Get the executable vertices and schedule them.
        JobFlowRun jobFlowRun = container.getJobFlowRun();
        DAG<Long, JobVertex, JobEdge> flow = jobFlowRun.getFlow();
        Set<JobVertex> executableVertices = JobFlowDagHelper.getExecutableVertices(flow);
        if (CollectionUtils.isNotEmpty(executableVertices)) {
            processVertices(executableVertices, container);
        }

        // step 3: update status in cache.
        container.setStatus(RUNNING);
        executableVertices.forEach(jobVertex -> jobVertex.setSubmitTime(LocalDateTime.now()));

        // TODO Special handle logic for streaming job?
        // step 4: if there are no executable vertices and all executed vertices in terminal state,
        // remove the container form scheduler.
        if (CollectionUtils.isEmpty(executableVertices)) {
            if (flow.getVertices().stream()
                    .filter(jobVertex -> jobVertex.getSubmitTime() != null)
                    .allMatch(
                            jobVertex ->
                                    jobVertex.getJobRunStatus() != null
                                            && jobVertex.getJobRunStatus().isTerminalState())) {
                IN_FLIGHT_FLOWS.remove(container.getId());
                JobFlowRun newJobFlowRun = new JobFlowRun();
                newJobFlowRun.setStatus(JobFlowDagHelper.getDagState(flow));
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void processVertices(Collection<JobVertex> jobVertices, FlowContainer container) {
        JobFlowRun jobFlowRun = container.getJobFlowRun();
        jobVertices.forEach(
                jobVertex -> {
                    JobInfo jobInfo = jobInfoService.getById(jobVertex.getJobId());
                    JobQuartzInfo jobQuartzInfo = new JobQuartzInfo(jobInfo);
                    jobQuartzInfo.addData(FLOW_RUN_ID, jobFlowRun.getId().toString());
                    quartzService.runOnce(jobQuartzInfo);
                });

        if (container.getStatus() == SUBMITTED) {
            // update JobFlowRun's status
            JobFlowRun newJobFlowRun = new JobFlowRun();
            newJobFlowRun.setId(jobFlowRun.getId());
            newJobFlowRun.setStatus(RUNNING);
            jobFlowRunService.updateById(newJobFlowRun);
        }
    }

    private void updateJobVertexStatus(FlowContainer container) {
        JobFlowRun jobFlowRun = container.getJobFlowRun();
        DAG<Long, JobVertex, JobEdge> flow = jobFlowRun.getFlow();
        List<Long> nonTerminalJobIdList =
                flow.getVertices().stream()
                        .filter(
                                jobVertex ->
                                        jobVertex.getJobRunStatus() == null
                                                || !jobVertex.getJobRunStatus().isTerminalState())
                        .map(JobVertex::getJobId)
                        .collect(toList());
        if (CollectionUtils.isEmpty(nonTerminalJobIdList)) {
            return;
        }

        // Get the latest status and update the job vertices' status in cache.
        List<JobRunInfo> jobRunInfoList =
                jobRunInfoService.list(
                        new QueryWrapper<JobRunInfo>()
                                .lambda()
                                .eq(JobRunInfo::getFlowRunId, jobFlowRun.getId())
                                .in(JobRunInfo::getJobId, nonTerminalJobIdList));
        for (JobRunInfo jobRunInfo : jobRunInfoList) {
            JobVertex jobVertex = flow.getVertex(jobRunInfo.getJobId());
            jobVertex.setJobRunId(jobRunInfo.getId());
            jobVertex.setJobRunStatus(ExecutionStatus.from(jobRunInfo.getStatus()));
        }
    }

    @Data
    static class FlowContainer {

        final String id;

        final JobFlowRun jobFlowRun;

        ExecutionStatus status;

        LocalDateTime submitTime;
    }

    class JobFlowExecuteThread implements Runnable {

        final FlowContainer container;

        public JobFlowExecuteThread(FlowContainer container) {
            this.container = Preconditions.checkNotNull(container);
        }

        @Override
        public void run() {
            synchronized (container) {
                ExecutionStatus status = container.getStatus();
                if (status == SUBMITTED || status == RUNNING) {
                    JobFlowScheduleService.this.processFlow(container);
                } else {
                    log.warn("Unexpected status: {}", status);
                }
            }
        }
    }
}
