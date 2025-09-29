package com.flink.platform.web.component;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.Worker;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.WorkerService;
import com.flink.platform.web.service.JobFlowScheduleService;
import com.flink.platform.web.util.ThreadUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Stream;

import static com.flink.platform.common.constants.Constant.HOSTNAME;
import static com.flink.platform.common.constants.Constant.HOST_IP;
import static com.flink.platform.common.constants.Constant.LOCALHOST;
import static com.flink.platform.common.enums.ExecutionStatus.getNonTerminals;
import static com.flink.platform.common.enums.WorkerStatus.DELETED;
import static com.flink.platform.common.enums.WorkerStatus.FOLLOWER;
import static com.flink.platform.common.enums.WorkerStatus.INACTIVE;
import static com.flink.platform.common.enums.WorkerStatus.LEADER;
import static com.flink.platform.common.enums.WorkerStatus.isActiveStatus;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.springframework.transaction.annotation.Isolation.SERIALIZABLE;

/**
 * Work instance. <br>
 *
 * <p>1. Update worker heartbeat.
 *
 * <p>2. Try to get leader role if no leader alive or there are more than one leader.
 *
 * <p>3. Check if any workers are inactive and reassign jobs owned by these workers.
 */
@Slf4j
@Component
public class WorkerHeartbeat {

    private static final ScheduledExecutorService EXECUTOR_SERVICE =
            ThreadUtil.newDaemonSingleScheduledExecutor("WorkerHeartbeat");

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
            @Value("${grpc.server.port}") int grpcPort) {
        this.workerService = workerService;
        this.jobFlowRunService = jobFlowRunService;
        this.jobFlowScheduleService = jobFlowScheduleService;
        this.port = port;
        this.grpcPort = grpcPort;
    }

    @PostConstruct
    public void initHeartbeat() {
        EXECUTOR_SERVICE.scheduleWithFixedDelay(this::heartbeat, new Random().nextInt(50) + 10, 60, SECONDS);
    }

    public synchronized void heartbeat() {
        // 1. Update worker heartbeat info.
        Worker worker = workerService.getCurWorkerIdAndRole();
        Long workerId = worker != null ? worker.getId() : null;

        worker = new Worker();
        worker.setId(workerId);
        worker.setHeartbeat(System.currentTimeMillis());
        if (workerId == null) {
            worker.setName(HOSTNAME);
            worker.setIp(HOST_IP);
            worker.setPort(port);
            worker.setGrpcPort(grpcPort);
            worker.setRole(FOLLOWER);
        }

        if (!isActiveStatus(worker.getRole())) {
            worker.setRole(FOLLOWER);
        }

        workerService.saveOrUpdate(worker);

        // 2. Try to be the leader.
        if (!hasValidLeader()) {
            assignLeaderRoleToWorker(worker.getId());
        }

        // 3. Reassign unfinished workflows belonging to offline workers.
        worker = workerService.getById(worker.getId());
        if (!LEADER.equals(worker.getRole())) {
            return;
        }

        List<JobFlowRun> unfinishedList = getInvalidWorkers().stream()
                .flatMap(this::getUnfinishedByWorker)
                .toList();
        if (unfinishedList.isEmpty()) {
            return;
        }

        // TODO: dispatch JobFlowRun to other active workers?
        unfinishedList.forEach(jobFlowRun -> jobFlowRun.setHost(HOST_IP));
        updateJobFlowRunHost(unfinishedList);
        unfinishedList.forEach(jobFlowScheduleService::registerToScheduler);
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

    @Transactional(isolation = SERIALIZABLE, rollbackFor = Throwable.class)
    public void assignLeaderRoleToWorker(long workerId) {
        if (hasValidLeader()) {
            return;
        }

        workerService.update(new UpdateWrapper<Worker>()
                .lambda()
                .set(Worker::getRole, FOLLOWER)
                .eq(Worker::getRole, LEADER));

        Worker worker = new Worker();
        worker.setId(workerId);
        worker.setRole(LEADER);
        workerService.updateById(worker);
    }

    public boolean hasValidLeader() {
        List<Worker> list = workerService.list(new QueryWrapper<Worker>()
                .lambda()
                .select(Worker::getId, Worker::getHeartbeat)
                .eq(Worker::getRole, LEADER)
                .ne(Worker::getIp, LOCALHOST));
        return list.size() == 1 && list.getFirst().isActive();
    }

    public List<Worker> getInvalidWorkers() {
        return workerService
                .list(new QueryWrapper<Worker>()
                        .lambda()
                        .ne(Worker::getRole, DELETED)
                        .ne(Worker::getIp, LOCALHOST))
                .stream()
                .filter(worker -> !worker.isActive() || INACTIVE.equals(worker.getRole()))
                .collect(toList());
    }

    public Stream<JobFlowRun> getUnfinishedByWorker(Worker invalidWorker) {
        return jobFlowRunService
                .list(new QueryWrapper<JobFlowRun>()
                        .lambda()
                        .eq(JobFlowRun::getHost, invalidWorker.getIp())
                        .in(JobFlowRun::getStatus, getNonTerminals()))
                .stream();
    }
}
