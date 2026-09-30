package com.flink.platform.alert;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/** Async execution resources dedicated to outbound alerts. */
@Configuration(proxyBeanMethods = false)
public class AlertConfig {

    public static final String ALERT_EXECUTOR = "alertExecutor";

    @Bean(name = ALERT_EXECUTOR)
    public Executor alertExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setVirtualThreads(true);
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("alert-send-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }
}
