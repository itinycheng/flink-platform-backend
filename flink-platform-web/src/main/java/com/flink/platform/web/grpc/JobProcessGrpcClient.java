package com.flink.platform.web.grpc;

import com.flink.platform.common.constants.Constant;
import com.flink.platform.dao.entity.Worker;
import com.flink.platform.grpc.JobGrpcServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.flink.platform.grpc.JobGrpcServiceGrpc.JobGrpcServiceBlockingStub;

/** Job processing grpc client. */
@Slf4j
@Service
public class JobProcessGrpcClient {

    @GrpcClient("local-grpc-server")
    private JobGrpcServiceBlockingStub localGrpcStub;

    private final Map<String, JobGrpcServiceBlockingStub> grpcStubMap = new ConcurrentHashMap<>();

    private final Object lock = new Object();

    public JobGrpcServiceBlockingStub grpcClient(Worker worker) {
        if (worker == null || Constant.HOST_IP.equals(worker.getIp())) {
            return localGrpcStub;
        }

        String key = stubKey(worker.getIp(), worker.getGrpcPort());
        JobGrpcServiceBlockingStub stub = grpcStubMap.get(key);
        if (stub != null) {
            return stub;
        }

        synchronized (lock) {
            if (stub != null) {
                return stub;
            }

            ManagedChannel channel =
                    ManagedChannelBuilder.forAddress(worker.getIp(), worker.getGrpcPort())
                            .keepAliveWithoutCalls(true)
                            .usePlaintext()
                            .build();
            stub = JobGrpcServiceGrpc.newBlockingStub(channel);
            grpcStubMap.put(key, stub);
        }
        return stub;
    }

    private String stubKey(String ip, int port) {
        return String.join(":", ip, String.valueOf(port));
    }

    @PreDestroy
    public void destroyStubs() {
        // TODO
    }
}
