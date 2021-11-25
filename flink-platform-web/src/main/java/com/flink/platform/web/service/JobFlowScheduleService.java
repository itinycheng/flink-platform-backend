package com.flink.platform.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.graph.DAG;
import com.flink.platform.common.model.JobEdge;
import com.flink.platform.common.model.JobVertex;
import com.flink.platform.dao.entity.JobFlowRun;
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
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.flink.platform.common.enums.ExecutionStatus.RUNNING;
import static com.flink.platform.common.enums.ExecutionStatus.SUBMITTED;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCEEDED;
import static com.flink.platform.web.entity.JobQuartzInfo.FLOW_RUN_ID;
import static java.util.stream.Collectors.toList;

/** schedule job flow. */
@Slf4j
@Service
public class JobFlowScheduleService {

    public static final Map<String, Object> FLOW_LOCK_MAP = new ConcurrentHashMap<>();

    public static final Map<String, FlowContainer> IN_FLIGHT_FLOWS =
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

    @Scheduled(cron = "0 0/5 * * * ?")
    public void schedule() {
        // job flow
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
                case UNDEFINED:
                default:
                    log.error(
                            "Job flow run :{} is in unexpected status {}",
                            jobFlowRun.getId(),
                            jobFlowRun.getStatus());
                    break;
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void processFlow(FlowContainer container) {

        // step 0: if not started, process immediately.
        if (container.getStatus() == SUBMITTED) {
            processVertices(container.pendingVertexMap.values(), container);
        }

        // TODO step 0: update interval check.

        // step 1: update status in pendingMap, and remove vertices in pendingMap whose status is
        // terminated.
        updateVertexStatusInPendingMap(container);

        Map<Long, JobVertex> pendingVertexMap = container.getPendingVertexMap();
        JobFlowRun jobFlowRun = container.getJobFlowRun();

        // step 2: precondition check and pending the executable vertices.

        // Get the edges whose status matched his formVertex's status.
        DAG<Long, JobVertex, JobEdge> flow = jobFlowRun.getFlow();
        List<JobEdge> statusMatchedEdgeList =
                pendingVertexMap.values().stream()
                        .flatMap(
                                fromVertex ->
                                        flow.getEdgesFromVertex(fromVertex).stream()
                                                .map(edge -> edge.unwrap(JobEdge.class))
                                                .filter(
                                                        edge ->
                                                                edge.getExpectStatus()
                                                                        == fromVertex
                                                                                .getJobRunStatus()))
                        .collect(toList());

        // Only execute edge with failed status, if there are any.
        List<JobEdge> failedEdges =
                statusMatchedEdgeList.stream()
                        .filter(edge -> edge.getExpectStatus().isErrTerminalState())
                        .collect(toList());
        if (CollectionUtils.isNotEmpty(failedEdges)) {
            statusMatchedEdgeList = failedEdges;
            pendingVertexMap.clear();
        }

        // Put the executable vertices into pendingMap.
        statusMatchedEdgeList.stream()
                .map(edge -> flow.getVertex(edge.getToVId()))
                .filter(toVertex -> JobFlowDagHelper.isPreconditionSatisfied(toVertex, flow))
                .filter(toVertex -> toVertex.getJobRunId() == null)
                .forEach(jobVertex -> pendingVertexMap.put(jobVertex.getId(), jobVertex));

        // Remove the fromVertices whose edge status is matched.
        statusMatchedEdgeList.forEach(edge -> pendingVertexMap.remove(edge.getFromVId()));

        // step 3: schedule vertex.
        List<JobVertex> toBeScheduledVertices =
                pendingVertexMap.values().stream()
                        .filter(vertex -> vertex.getJobRunId() == null)
                        .collect(toList());
        processVertices(toBeScheduledVertices, container);

        // step 4: if there are no unreached vertices, remove the container form scheduler.
        List<JobVertex> unreachedVertex =
                pendingVertexMap.values().stream()
                        .flatMap(vertex -> flow.getNextVertices(vertex).stream())
                        .collect(toList());
        if (CollectionUtils.isEmpty(unreachedVertex)) {
            // TODO change job flow run status in mysql.
            container.setStatus(SUCCEEDED);
            IN_FLIGHT_FLOWS.remove(container.getId());
        }
    }

    private void updateVertexStatusInPendingMap(FlowContainer container) {
        JobFlowRun jobFlowRun = container.getJobFlowRun();
        Map<Long, JobVertex> pendingVertexMap = container.getPendingVertexMap();
        List<Long> nonTerminalJobIdList =
                pendingVertexMap.values().stream()
                        .filter(
                                jobVertex ->
                                        jobVertex.getJobRunStatus() == null
                                                || !jobVertex.getJobRunStatus().isTerminalState())
                        .map(JobVertex::getJobId)
                        .collect(toList());
        List<JobRunInfo> jobRunInfoList =
                jobRunInfoService.list(
                        new QueryWrapper<JobRunInfo>()
                                .lambda()
                                .eq(JobRunInfo::getFlowRunId, jobFlowRun.getId())
                                .in(JobRunInfo::getJobId, nonTerminalJobIdList));

        // Update job vertex status.
        for (JobRunInfo jobRunInfo : jobRunInfoList) {
            JobVertex jobVertex = pendingVertexMap.get(jobRunInfo.getJobId());
            jobVertex.setJobRunId(jobRunInfo.getId());
            jobVertex.setJobRunStatus(ExecutionStatus.from(jobRunInfo.getStatus()));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void processVertices(Collection<JobVertex> jobVertices, FlowContainer container) {
        JobFlowRun jobFlowRun = container.getJobFlowRun();
        List<Long> toBeExecutedJobIds =
                jobVertices.stream().map(JobVertex::getJobId).collect(toList());

        // schedule job.
        jobInfoService
                .listByIds(toBeExecutedJobIds)
                .forEach(
                        jobInfo -> {
                            JobQuartzInfo jobQuartzInfo = new JobQuartzInfo(jobInfo);
                            jobQuartzInfo.addData(FLOW_RUN_ID, jobFlowRun.getId().toString());
                            quartzService.runOnce(jobQuartzInfo);
                        });

        // update JobFlowRun's status
        JobFlowRun newJobFlowRun = new JobFlowRun();
        newJobFlowRun.setId(jobFlowRun.getId());
        newJobFlowRun.setStatus(RUNNING);
        jobFlowRunService.updateById(newJobFlowRun);

        // update container
        container.setProcessTime(LocalDateTime.now());
        container.setStatus(RUNNING);
    }

    public void registerToScheduler(JobFlowRun jobFlowRun) {
        // cache job flow run.
        Map<Long, JobVertex> pendingVertexMap =
                jobFlowRun.getFlow().getBeginVertices().stream()
                        .collect(Collectors.toMap(JobVertex::getId, jobVertex -> jobVertex));
        String flowKey =
                String.join(
                        "-", jobFlowRun.getPriority().toString(), jobFlowRun.getId().toString());

        FlowContainer container = new FlowContainer(flowKey, jobFlowRun, pendingVertexMap);
        container.setStatus(SUBMITTED);
        IN_FLIGHT_FLOWS.put(flowKey, container);
    }

    @Data
    static class FlowContainer {

        final String id;

        final JobFlowRun jobFlowRun;

        final Map<Long, JobVertex> pendingVertexMap;

        ExecutionStatus status;

        LocalDateTime processTime;
    }

    class JobFlowExecuteThread implements Runnable {

        FlowContainer container;

        public JobFlowExecuteThread(FlowContainer container) {
            this.container = container;
        }

        @Override
        public void run() {
            synchronized (getProcessLock(container.getId())) {
                ExecutionStatus status = container.getStatus();
                if (status == SUBMITTED || status == RUNNING) {
                    JobFlowScheduleService.this.processFlow(container);
                } else {
                    log.warn("Unexpected status: {}", status);
                }
            }
        }

        private Object getProcessLock(String flowKey) {
            Object newLock = new Object();
            Object lock = FLOW_LOCK_MAP.putIfAbsent(flowKey, newLock);
            if (lock == null) {
                lock = newLock;
            }
            return lock;
        }
    }
}
