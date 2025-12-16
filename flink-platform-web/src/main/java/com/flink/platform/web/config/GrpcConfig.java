package com.flink.platform.web.config;

import com.flink.platform.grpc.JobGrpcServiceGrpc;
import com.flink.platform.web.util.ThreadUtil;
import io.grpc.ServerBuilder;
import io.grpc.netty.NettyServerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.grpc.server.ServerBuilderCustomizer;

/**
 * Use virtual thread to handle grpc calls.
 */
@SuppressWarnings("unused")
@Configuration
class GrpcConfig {

    @Bean
    public <T extends ServerBuilder<T>> ServerBuilderCustomizer<T> serverConfigurer() {
        return serverBuilder -> {
            if (serverBuilder instanceof NettyServerBuilder builder) {
                builder.executor(ThreadUtil.newVirtualThreadExecutor("GrpcServerThread"))
                        .permitKeepAliveWithoutCalls(true);
            }
        };
    }

    @Bean
    public JobGrpcServiceGrpc.JobGrpcServiceBlockingStub stub(GrpcChannelFactory channels) {
        return JobGrpcServiceGrpc.newBlockingStub(channels.createChannel("default"));
    }
}
