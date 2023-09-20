package com.flink.platform.web.grpc;

import com.flink.platform.common.constants.Constant;
import com.flink.platform.dao.entity.Worker;
import com.flink.platform.grpc.JobGrpcServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.flink.platform.grpc.JobGrpcServiceGrpc.JobGrpcServiceBlockingStub;

/** Job processing grpc client. */
@Slf4j
@Service
public class JobProcessGrpcClient {

    @GrpcClient("local-grpc-server")
    private JobGrpcServiceBlockingStub localGrpcStub;

    private final Map<String, JobGrpcServiceBlockingStub> grpcStubMap = new ConcurrentHashMap<>();

    private final Map<String, ManagedChannel> channelMap = new ConcurrentHashMap<>();

    private final Object lock = new Object();

    public JobGrpcServiceBlockingStub grpcClient(Worker worker) {
        if (worker == null
                || StringUtils.isEmpty(worker.getIp())
                || Constant.LOCALHOST.equals(worker.getIp())
                || Constant.HOST_IP.equals(worker.getIp())) {
            return localGrpcStub;
        }

        String key = stubKey(worker.getIp(), worker.getGrpcPort());
        JobGrpcServiceBlockingStub stub = grpcStubMap.get(key);
        if (stub != null) {
            return stub;
        }

        synchronized (lock) {
            stub = grpcStubMap.get(key);
            if (stub != null) {
                return stub;
            }

            ManagedChannel channel =
                    channelMap.computeIfAbsent(
                            key,
                            s ->
                                    ManagedChannelBuilder.forAddress(
                                                    worker.getIp(), worker.getGrpcPort())
                                            .keepAliveWithoutCalls(true)
                                            .usePlaintext()
                                            .build());
            stub = JobGrpcServiceGrpc.newBlockingStub(channel);
            grpcStubMap.put(key, stub);
        }
        return stub;
    }

    private String stubKey(String ip, int port) {
        return String.join(":", ip, String.valueOf(port));
    }

    @PreDestroy
    public void destroy() {
        log.debug("Initiating manually created grpc ManagedChannel shutdown.");
        channelMap.forEach(
                (key, channel) -> {
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
