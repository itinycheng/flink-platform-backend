package com.flink.platform.web.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Worker config. */
@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "worker")
public class WorkerConfig {

    private int flowExecThreads;

    private int perFlowExecThreads;

    private int errorRetries;

    private int streamingJobToSuccessMills;
}
