package com.flink.platform.web.grpc;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.dao.entity.Worker;
import com.flink.platform.dao.service.WorkerService;
import com.flink.platform.grpc.JobGrpcServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.flink.platform.common.constants.Constant.HOST_IP;
import static com.flink.platform.common.constants.Constant.LOCALHOST;
import static com.flink.platform.grpc.JobGrpcServiceGrpc.JobGrpcServiceBlockingStub;

/** Job processing grpc client. */
@Slf4j
@Service
public class JobGrpcClient {

    @Autowired
    private WorkerService workerService;

    @Autowired
    private JobGrpcServiceBlockingStub localGrpcStub;

    private final Map<String, JobGrpcServiceBlockingStub> grpcStubMap = new ConcurrentHashMap<>();

    private final Map<String, ManagedChannel> channelMap = new ConcurrentHashMap<>();

    public JobGrpcServiceBlockingStub grpcClient(String ip) {
        if (StringUtils.isEmpty(ip) || LOCALHOST.equals(ip) || HOST_IP.equals(ip)) {
            return localGrpcStub;
        }

        String key = stubKey(ip);
        JobGrpcServiceBlockingStub stub = grpcStubMap.get(key);
        if (stub != null) {
            return stub;
        }

        Worker worker = workerService.getOne(new QueryWrapper<Worker>().lambda().eq(Worker::getIp, ip));
        return grpcClient(key, worker);
    }

    // this method is called in the virtual thread.
    private synchronized JobGrpcServiceBlockingStub grpcClient(String key, Worker worker) {
        JobGrpcServiceBlockingStub stub = grpcStubMap.get(key);
        if (stub != null) {
            return stub;
        }

        ManagedChannel channel = channelMap.computeIfAbsent(
                key, s -> ManagedChannelBuilder.forAddress(worker.getIp(), worker.getGrpcPort())
                        .keepAliveWithoutCalls(true)
                        .usePlaintext()
                        .build());
        stub = JobGrpcServiceGrpc.newBlockingStub(channel);
        grpcStubMap.put(key, stub);
        return stub;
    }

    private String stubKey(String ip) {
        return String.valueOf(ip).trim();
    }

    @PreDestroy
    public void destroy() {
        log.debug("Initiating manually created grpc ManagedChannel shutdown.");
        channelMap.forEach((key, channel) -> {
            try {
                channel.shutdown().awaitTermination(2, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Shutdown grpc ManagedChannel failed, host: {}", key, e);
            }
        });

        grpcStubMap.clear();
        channelMap.clear();
        log.info("Shutdown manually created grpc ManagedChannel successfully.");
    }
}
