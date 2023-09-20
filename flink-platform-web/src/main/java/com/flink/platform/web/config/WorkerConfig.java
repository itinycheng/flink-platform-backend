package com.flink.platform.web.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/** Worker config. */
@Setter
@Getter
@Validated
@Configuration
@ConfigurationProperties(prefix = "worker")
public class WorkerConfig {

    @Min(1)
    private int flowExecThreads;

    @Min(1)
    private int perFlowExecThreads;

    @Min(0)
    private int errorRetries;

    @Min(5_000)
    private int streamingJobToSuccessMills;

    @Min(1)
    private int reactiveExecThreads;

    @Min(20_000)
    private long flinkSubmitTimeoutMills;

    @Min(1_000)
    @Max(24 * 60 * 60 * 1000)
    private long maxShellExecTimeoutMills;
}
