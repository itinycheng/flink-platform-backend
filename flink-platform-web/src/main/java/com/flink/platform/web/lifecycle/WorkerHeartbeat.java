package com.flink.platform.web.lifecycle;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.util.ExceptionUtil;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.Worker;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.WorkerService;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.service.JobFlowScheduleService;
import com.flink.platform.web.util.ThreadUtil;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Stream;

import static com.flink.platform.common.constants.Constant.HOSTNAME;
import static com.flink.platform.common.constants.Constant.HOST_IP;
import static com.flink.platform.common.constants.Constant.LOCALHOST;
import static com.flink.platform.common.enums.ExecutionStatus.getNonTerminals;
import static com.flink.platform.common.enums.WorkerStatus.ACTIVE;
import static com.flink.platform.common.enums.WorkerStatus.DELETED;
import static com.flink.platform.common.enums.WorkerStatus.INACTIVE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;

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

    private final JobFlowScheduleService jobFlowScheduleService;

    private final String port;

    private final int grpcPort;

    @Autowired
    public WorkerHeartbeat(
            WorkerService workerService,
            JobFlowRunService jobFlowRunService,
            JobFlowScheduleService jobFlowScheduleService,
            @Value("${server.port}") String port,
            @Value("${spring.grpc.server.port}") int grpcPort) {
        this.workerService = workerService;
        this.jobFlowRunService = jobFlowRunService;
        this.jobFlowScheduleService = jobFlowScheduleService;
        this.port = port;
        this.grpcPort = grpcPort;
    }

    public void heartbeat() {
        var stopwatch = StopWatch.createStarted();
        reportStatus();
        recoverJobs();

        stopwatch.stop();
        log.info("Worker heartbeat completed, cost {} ms", stopwatch.getTime());
    }

    public void reportStatus() {
        var worker = workerService.getCurWorkerIdAndRole();
        var workerId = worker != null ? worker.getId() : null;

        var temp = new Worker();
        temp.setId(workerId);
        temp.setHeartbeat(System.currentTimeMillis());
        temp.setRole(ACTIVE);
        if (workerId == null) {
            temp.setName(HOSTNAME);
            temp.setIp(HOST_IP);
            temp.setPort(port);
            temp.setGrpcPort(grpcPort);
        }
        workerService.saveOrUpdate(temp);
    }

    @SchedulerLock(name = "WorkerHeartbeat_recoverJobs", lockAtMostFor = "PT30S", lockAtLeastFor = "PT20S")
    public void recoverJobs() {
        var pendingList = getInvalidWorkers().stream()
                .flatMap(this::getUnfinishedByWorker)
                .toList();
        if (pendingList.isEmpty()) {
            return;
        }

        // TODO: dispatch JobFlowRun to other active workers?
        pendingList.forEach(jobFlowRun -> jobFlowRun.setHost(HOST_IP));
        updateJobFlowRunHost(pendingList);
        pendingList.forEach(jobFlowScheduleService::registerToScheduler);
    }

    private void updateJobFlowRunHost(List<JobFlowRun> list) {
        jobFlowRunService.updateBatchById(list.stream()
                .map(jobFlowRun -> {
                    JobFlowRun newJobFlowRun = new JobFlowRun();
                    newJobFlowRun.setId(jobFlowRun.getId());
                    newJobFlowRun.setHost(HOST_IP);
                    return newJobFlowRun;
                })
                .toList());
    }

    private List<Worker> getInvalidWorkers() {
        return workerService
                .list(new QueryWrapper<Worker>()
                        .lambda()
                        .ne(Worker::getRole, DELETED)
                        .ne(Worker::getIp, LOCALHOST))
                .stream()
                .filter(worker -> !worker.isActive() || INACTIVE.equals(worker.getRole()))
                .collect(toList());
    }

    private Stream<JobFlowRun> getUnfinishedByWorker(Worker invalidWorker) {
        return jobFlowRunService
                .list(new QueryWrapper<JobFlowRun>()
                        .lambda()
                        .eq(JobFlowRun::getHost, invalidWorker.getIp())
                        .in(JobFlowRun::getStatus, getNonTerminals()))
                .stream();
    }

    public static class Scheduler {

        private static final ScheduledExecutorService EXECUTOR = createExecutor();

        private static boolean started = false;

        public static synchronized void start() {
            if (started) {
                log.warn("Worker heartbeat scheduler already started.");
                return;
            }

            final var service = SpringContext.getBean(WorkerHeartbeat.class);
            EXECUTOR.scheduleWithFixedDelay(
                    () -> ExceptionUtil.runWithErrorLogging(service::heartbeat), 0, 30, SECONDS);
            started = true;
            log.info("Worker heartbeat scheduler started.");
        }

        private static ScheduledExecutorService createExecutor() {
            var executor = ThreadUtil.newDaemonSingleScheduledExecutor("worker-heartbeat");
            ThreadUtil.addShutdownHook(executor, "worker-heartbeat");
            return executor;
        }
    }
}
