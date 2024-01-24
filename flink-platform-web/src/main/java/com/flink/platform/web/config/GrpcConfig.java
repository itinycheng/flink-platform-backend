package com.flink.platform.web.config;

import com.flink.platform.web.util.ThreadUtil;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Use virtual thread to handle grpc calls.
 */
@Configuration
class GrpcConfig {

    @Bean
    public GrpcServerConfigurer serverConfigurer() {
        return serverBuilder -> {
            if (serverBuilder instanceof NettyServerBuilder) {
                ((NettyServerBuilder) serverBuilder)
                        .executor(ThreadUtil.newVirtualThreadExecutor("GrpcServerThread"))
                        .permitKeepAliveWithoutCalls(true);
            }
        };
    }
}
