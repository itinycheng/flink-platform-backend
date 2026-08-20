package com.flink.platform.web.lifecycle;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.alert.AlertSendingService;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.Worker;
import com.flink.platform.dao.entity.Workspace;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.WorkerService;
import com.flink.platform.dao.service.WorkspaceService;
import com.flink.platform.environment.EnvironmentRegistry;
import com.flink.platform.web.service.WorkerSelectService;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.flink.platform.common.constants.Constant.HOSTNAME;
import static com.flink.platform.common.constants.Constant.HOST_IP;
import static com.flink.platform.common.constants.Constant.LOCALHOST;
import static com.flink.platform.common.enums.ExecutionStatus.getNonTerminals;
import static com.flink.platform.common.enums.WorkerStatus.ACTIVE;
import static com.flink.platform.common.enums.WorkerStatus.DELETED;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * Work instance. <br>
 *
 * <p>1. Update worker heartbeat.
 *
 * <p>2. Check if any workers are inactive and reassign jobs owned by these workers.
 */
@Slf4j
@Component
public class WorkerHeartbeat {

    private final WorkerService workerService;

    private final JobFlowRunService jobFlowRunService;

    private final EnvironmentRegistry registry;

    private final WorkspaceService workspaceService;

    private final WorkerSelectService workerSelectService;

    private final AlertSendingService alertSendingService;

    private final WorkerHeartbeat self;

    private final String port;

    private final int grpcPort;

    @Autowired
    public WorkerHeartbeat(
            @Lazy WorkerHeartbeat self,
            WorkerService workerService,
            JobFlowRunService jobFlowRunService,
            EnvironmentRegistry registry,
            WorkspaceService workspaceService,
            WorkerSelectService workerSelectService,
            AlertSendingService alertSendingService,
            @Value("${server.port}") String port,
            @Value("${spring.grpc.server.port}") int grpcPort) {
        this.self = self;
        this.workerService = workerService;
        this.jobFlowRunService = jobFlowRunService;
        this.registry = registry;
        this.workspaceService = workspaceService;
        this.workerSelectService = workerSelectService;
        this.alertSendingService = alertSendingService;
        this.port = port;
        this.grpcPort = grpcPort;
    }

    @Scheduled(fixedDelay = 30, timeUnit = SECONDS)
    public void heartbeat() {
        var stopwatch = StopWatch.createStarted();
        reportHeartbeat();
        self.reassignOrphans();

        stopwatch.stop();
        log.info("Worker heartbeat completed, cost {} ms", stopwatch.getTime());
    }

    public void reportHeartbeat() {
        var worker = workerService.getCurWorkerIdAndRole();
        var workerId = worker != null ? worker.getId() : null;

        var tmp = new Worker();
        tmp.setId(workerId);
        tmp.setHeartbeat(System.currentTimeMillis());
        tmp.setRole(ACTIVE);
        tmp.setEnvironments(registry.specs());
        if (workerId == null) {
            tmp.setName(HOSTNAME);
            tmp.setIp(HOST_IP);
            tmp.setPort(port);
            tmp.setGrpcPort(grpcPort);
        }
        workerService.saveOrUpdate(tmp);
    }

    @SchedulerLock(name = "WorkerHeartbeat_reassignOrphans", lockAtMostFor = "PT30S", lockAtLeastFor = "PT20S")
    public void reassignOrphans() {
        getUnhealthyWorkers().forEach(this::reassignFlowRunsOf);
    }

    private void reassignFlowRunsOf(Worker deadWorker) {
        var flowRuns = getNonTerminalFlowRuns(deadWorker);
        if (CollectionUtils.isEmpty(flowRuns)) {
            return;
        }

        var activeWorkerMap = workerSelectService.mapActiveWorkersById();
        var workspaceIds =
                flowRuns.stream().map(JobFlowRun::getWorkspaceId).distinct().collect(toList());
        var workspaceMap = workspaceService.listByIds(workspaceIds).stream().collect(toMap(Workspace::getId, w -> w));

        var reassigned = new ArrayList<JobFlowRun>();
        for (var flowRun : flowRuns) {
            var workspace = workspaceMap.get(flowRun.getWorkspaceId());
            var workspaceWorkerIds = workspace != null && workspace.getConfig() != null
                    ? workspace.getConfig().getWorkers()
                    : null;
            if (CollectionUtils.isEmpty(workspaceWorkerIds)) {
                log.error("No workspace/worker found for flow run {}", flowRun.getId());
                continue;
            }

            var target = workerSelectService.randomWorker(workspaceWorkerIds, activeWorkerMap);
            if (target == null) {
                log.error(
                        "Workspace {} has no active worker; flow run {} held for recovery",
                        flowRun.getWorkspaceId(),
                        flowRun.getId());
                alertSendingService.sendAlerts(
                        flowRun, "No active worker in workspace; flow run held for recovery on the dead host");
                continue;
            }

            var newFlowRun = new JobFlowRun();
            newFlowRun.setId(flowRun.getId());
            newFlowRun.setHost(target.getIp());
            reassigned.add(newFlowRun);
        }

        if (!reassigned.isEmpty()) {
            jobFlowRunService.updateBatchById(reassigned);
        }
    }

    private List<Worker> getUnhealthyWorkers() {
        return workerService
                .list(new QueryWrapper<Worker>()
                        .lambda()
                        .ne(Worker::getRole, DELETED)
                        .ne(Worker::getIp, LOCALHOST))
                .stream()
                .filter(worker -> !worker.isActive())
                .collect(toList());
    }

    private List<JobFlowRun> getNonTerminalFlowRuns(Worker worker) {
        return jobFlowRunService.list(new QueryWrapper<JobFlowRun>()
                .lambda()
                .eq(JobFlowRun::getHost, worker.getIp())
                .in(JobFlowRun::getStatus, getNonTerminals()));
    }
}
