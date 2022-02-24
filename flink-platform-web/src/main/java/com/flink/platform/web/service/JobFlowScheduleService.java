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
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.flink.platform.common.enums.ExecutionStatus.RUNNING;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static com.flink.platform.web.entity.JobQuartzInfo.JOB_RUN_ID;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

/** schedule job flow. */
@Slf4j
@Component
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
                    new BasicThreadFactory.Builder()
                            .namingPattern("JobFlowExecutor-%d")
                            .daemon(false)
                            .build());

    private static final List<Integer> TERMINAL_STATUS_LIST =
            ExecutionStatus.getTerminals().stream().map(ExecutionStatus::getCode).collect(toList());

    @Autowired private QuartzService quartzService;

    @Autowired private JobInfoService jobInfoService;

    @Autowired private JobRunInfoService jobRunInfoService;

    @Autowired private JobFlowRunService jobFlowRunService;

    @Scheduled(fixedDelay = 10_000)
    public void scheduleJobFlow() {
        // 1. Get the latest status and update the job vertices' status in cache.
        Map<Long, List<JobRunInfo>> jobRunInFlightMap = getJobRunStatusInFlightMap();

        // 2. process job vertices.
        for (FlowContainer container : IN_FLIGHT_FLOWS.values()) {
            JobFlowRun jobFlowRun = container.getJobFlowRun();
            switch (container.getStatus()) {
                case SUBMITTED:
                    EXECUTOR.execute(new JobFlowExecuteThread(container));
                    break;
                case RUNNING:
                    List<JobRunInfo> jobRunInfoList = jobRunInFlightMap.get(jobFlowRun.getId());
                    if (CollectionUtils.isNotEmpty(jobRunInfoList)) {
                        jobRunInfoList.forEach(
                                jobRunInfo -> {
                                    JobVertex vertex =
                                            jobFlowRun.getFlow().getVertex(jobRunInfo.getJobId());
                                    vertex.setJobRunStatus(
                                            ExecutionStatus.from(jobRunInfo.getStatus()));
                                });
                        EXECUTOR.execute(new JobFlowExecuteThread(container));
                    }
                    break;
                case SUCCESS:
                case KILLED:
                case FAILURE:
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

        String flowKey = genInFlightFlowKey(jobFlowRun);
        if (IN_FLIGHT_FLOWS.containsKey(flowKey)) {
            log.warn("The JobFlowRun already registered, jobFlowRun: {} ", jobFlowRun);
            return;
        }

        FlowContainer container = new FlowContainer(flowKey, jobFlowRun);
        container.setStatus(SUBMITTED);
        container.setSubmitTime(LocalDateTime.now());
        IN_FLIGHT_FLOWS.put(flowKey, container);
    }

    public void terminateFlow(Long flowRunId, ExecutionStatus status) {
        if (flowRunId == null) {
            return;
        }

        JobFlowRun jobFlowRun = jobFlowRunService.getById(flowRunId);
        String flowKey = genInFlightFlowKey(jobFlowRun);
        IN_FLIGHT_FLOWS.remove(flowKey);
        JobFlowRun newJobFlowRun = new JobFlowRun();
        newJobFlowRun.setId(jobFlowRun.getId());
        newJobFlowRun.setStatus(status);
        jobFlowRunService.updateById(newJobFlowRun);
    }

    public void processFlow(FlowContainer container) {
        // step 1: Get the executable vertices and schedule them.
        JobFlowRun jobFlowRun = container.getJobFlowRun();
        DAG<Long, JobVertex, JobEdge> flow = jobFlowRun.getFlow();
        Set<JobVertex> executableVertices = JobFlowDagHelper.getExecutableVertices(flow);
        if (CollectionUtils.isNotEmpty(executableVertices)) {
            Map<Long, JobRunInfo> processedJobRunMap =
                    processVertices(executableVertices, container);
            executableVertices.forEach(
                    jobVertex -> {
                        JobRunInfo jobRunInfo = processedJobRunMap.get(jobVertex.getJobId());
                        jobVertex.setJobRunId(jobRunInfo.getId());
                        jobVertex.setJobRunStatus(ExecutionStatus.from(jobRunInfo.getStatus()));
                        jobVertex.setSubmitTime(jobRunInfo.getSubmitTime());
                    });
        }

        // step 3: update status in cache.
        container.setStatus(RUNNING);

        // TODO Special handle logic for streaming job and update flow status?
        // step 4: if there are no executable vertices and all executed vertices in terminal state,
        // remove the container form scheduler.
        if (CollectionUtils.isEmpty(executableVertices)) {
            if (flow.getVertices().stream()
                    .filter(jobVertex -> jobVertex.getSubmitTime() != null)
                    .allMatch(
                            jobVertex ->
                                    jobVertex.getJobRunStatus() != null
                                            && jobVertex.getJobRunStatus().isTerminalState())) {
                ExecutionStatus status = JobFlowDagHelper.getDagState(flow);
                terminateFlow(jobFlowRun.getId(), status);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<Long, JobRunInfo> processVertices(
            Collection<JobVertex> jobVertices, FlowContainer container) {
        Map<Long, JobRunInfo> jobRunInfoList = new HashMap<>(jobVertices.size());
        JobFlowRun jobFlowRun = container.getJobFlowRun();
        for (JobVertex jobVertex : jobVertices) {
            JobInfo jobInfo = jobInfoService.getById(jobVertex.getJobId());
            JobRunInfo jobRunInfo = new JobRunInfo();
            jobRunInfo.setJobId(jobInfo.getId());
            jobRunInfo.setDeployMode(jobInfo.getDeployMode());
            jobRunInfo.setFlowRunId(jobFlowRun.getId());
            jobRunInfo.setStatus(SUBMITTED.getCode());
            jobRunInfo.setSubmitTime(LocalDateTime.now());
            jobRunInfoService.save(jobRunInfo);

            JobQuartzInfo jobQuartzInfo = new JobQuartzInfo(jobInfo);
            jobQuartzInfo.addData(JOB_RUN_ID, jobRunInfo.getId());
            quartzService.runOnce(jobQuartzInfo);

            jobRunInfoList.put(jobRunInfo.getJobId(), jobRunInfo);
        }

        if (container.getStatus() == SUBMITTED) {
            // update JobFlowRun's status
            JobFlowRun newJobFlowRun = new JobFlowRun();
            newJobFlowRun.setId(jobFlowRun.getId());
            newJobFlowRun.setStatus(RUNNING);
            jobFlowRunService.updateById(newJobFlowRun);
        }

        return jobRunInfoList;
    }

    private Map<Long, List<JobRunInfo>> getJobRunStatusInFlightMap() {
        // TODO update interval check, handle begin vertices, use statusChanged.
        List<Long> unterminatedJobRunIdList =
                IN_FLIGHT_FLOWS.values().stream()
                        .filter(container -> container.getStatus() != SUBMITTED)
                        .flatMap(
                                container ->
                                        container.getJobFlowRun().getFlow().getVertices().stream())
                        .filter(
                                jobVertex ->
                                        jobVertex.getJobRunStatus() != null
                                                && !jobVertex.getJobRunStatus().isTerminalState())
                        .map(JobVertex::getJobRunId)
                        .filter(Objects::nonNull)
                        .collect(toList());

        return ListUtils.partition(unterminatedJobRunIdList, 1000).stream()
                .map(
                        jobRunIdList ->
                                jobRunInfoService.list(
                                        new QueryWrapper<JobRunInfo>()
                                                .lambda()
                                                .in(JobRunInfo::getId, jobRunIdList)
                                                .in(JobRunInfo::getStatus, TERMINAL_STATUS_LIST)))
                .flatMap(Collection::stream)
                .collect(groupingBy(JobRunInfo::getFlowRunId));
    }

    private String genInFlightFlowKey(JobFlowRun jobFlowRun) {
        return String.join(
                "-", String.valueOf(jobFlowRun.getPriority()), String.valueOf(jobFlowRun.getId()));
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
