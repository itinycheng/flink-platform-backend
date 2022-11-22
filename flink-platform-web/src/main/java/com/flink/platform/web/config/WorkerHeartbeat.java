package com.flink.platform.web.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.dao.entity.Worker;
import com.flink.platform.dao.service.WorkerService;
import com.flink.platform.web.util.ThreadUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ScheduledExecutorService;

import static com.flink.platform.common.constants.Constant.HOSTNAME;
import static com.flink.platform.common.constants.Constant.HOST_IP;
import static com.flink.platform.common.enums.WorkerStatus.ACTIVE;
import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * Work instance. <br>
 * TODO: check if any workers are inactive and reassign jobs owned by these workers.
 */
@Configuration
public class WorkerHeartbeat {

    private static final ScheduledExecutorService EXECUTOR_SERVICE =
            ThreadUtil.newDaemonSingleScheduledExecutor("WorkerHeartbeat");

    @Autowired WorkerService workerService;

    @Value("${server.port}")
    private String port;

    @Value("${grpc.server.port}")
    private int grpcPort;

    public WorkerHeartbeat() {
        if (StringUtils.isBlank(HOST_IP)) {
            throw new RuntimeException(String.format("Host ip: %s is invalid", HOST_IP));
        }

        EXECUTOR_SERVICE.scheduleAtFixedRate(this::heartbeat, 1, 1, MINUTES);
    }

    public synchronized void heartbeat() {
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
            instance.setStatus(ACTIVE);
        }
        workerService.saveOrUpdate(instance);
    }
}
