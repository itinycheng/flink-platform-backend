package com.flink.platform.web.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.dao.entity.JobFlowRun;
import com.flink.platform.dao.entity.Worker;
import com.flink.platform.dao.service.JobFlowRunService;
import com.flink.platform.dao.service.WorkerService;
import com.flink.platform.web.service.JobFlowScheduleService;
import com.flink.platform.web.util.ThreadUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;

import static com.flink.platform.common.constants.Constant.HOSTNAME;
import static com.flink.platform.common.constants.Constant.HOST_IP;
import static com.flink.platform.common.enums.ExecutionStatus.getNonTerminals;
import static com.flink.platform.common.enums.WorkerStatus.FOLLOWER;
import static com.flink.platform.common.enums.WorkerStatus.INACTIVE;
import static com.flink.platform.common.enums.WorkerStatus.LEADER;
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
@Configuration
public class WorkerHeartbeat {

    private static final ScheduledExecutorService EXECUTOR_SERVICE =
            ThreadUtil.newDaemonSingleScheduledExecutor("WorkerHeartbeat");

    @Autowired WorkerService workerService;

    @Autowired JobFlowRunService jobFlowRunService;

    @Autowired JobFlowScheduleService jobFlowScheduleService;

    @Value("${server.port}")
    private String port;

    @Value("${grpc.server.port}")
    private int grpcPort;

    public WorkerHeartbeat() {
        if (StringUtils.isBlank(HOST_IP)) {
            throw new RuntimeException(String.format("Host ip: %s is invalid", HOST_IP));
        }

        EXECUTOR_SERVICE.scheduleWithFixedDelay(
                this::heartbeat, new Random().nextInt(50) + 10, 60, SECONDS);
    }

    public synchronized void heartbeat() {
        // 1. Update worker heartbeat info.
        Worker worker =
                workerService.getOne(
                        new QueryWrapper<Worker>()
                                .lambda()
                                .select(Worker::getId)
                                .eq(Worker::getIp, HOST_IP)
                                .last("LIMIT 1"));

        Worker instance = new Worker();
        instance.setId(worker != null ? worker.getId() : null);
        instance.setHeartbeat(System.currentTimeMillis());
        if (worker == null) {
            instance.setName(HOSTNAME);
            instance.setIp(HOST_IP);
            instance.setPort(port);
            instance.setGrpcPort(grpcPort);
            instance.setRole(FOLLOWER);
        }
        workerService.saveOrUpdate(instance);

        // 2. Try to be the leader.
        if (!hasValidLeader()) {
            try {
                assignLeaderRoleToWorker(instance.getId());
            } catch (Throwable e) {
                log.error("Failed to assign leader role to worker", e);
            }
        }

        // 3. Reassign unfinished workflows belonging to offline workers.
        // TODO: dispatch JobFlowRun to other active workers.
        getWorkersWithHeartbeatTimeout()
                .forEach(
                        timeoutWorker ->
                                jobFlowRunService
                                        .list(
                                                new QueryWrapper<JobFlowRun>()
                                                        .lambda()
                                                        .eq(
                                                                JobFlowRun::getHost,
                                                                timeoutWorker.getIp())
                                                        .in(
                                                                JobFlowRun::getStatus,
                                                                getNonTerminals()))
                                        .forEach(
                                                jobFlowRun ->
                                                        jobFlowScheduleService.rebuildAndSchedule(
                                                                jobFlowRun)));
    }

    @Transactional(isolation = SERIALIZABLE, rollbackFor = Throwable.class)
    public void assignLeaderRoleToWorker(long workerId) {
        if (hasValidLeader()) {
            return;
        }

        Worker worker = new Worker();
        worker.setId(workerId);
        worker.setRole(LEADER);
        workerService.updateById(worker);
    }

    public boolean hasValidLeader() {
        List<Worker> list =
                workerService.list(
                        new QueryWrapper<Worker>()
                                .lambda()
                                .select(Worker::getId, Worker::getHeartbeat)
                                .eq(Worker::getRole, LEADER));
        return list.size() == 1 && list.get(0).isActive();
    }

    public List<Worker> getWorkersWithHeartbeatTimeout() {
        return workerService.list(new QueryWrapper<Worker>().lambda().ne(Worker::getRole, INACTIVE))
                .stream()
                .filter(worker -> !worker.isActive())
                .collect(toList());
    }
}
