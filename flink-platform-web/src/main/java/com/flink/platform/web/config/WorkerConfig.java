package com.flink.platform.web.config;

import com.flink.platform.common.validation.DurationRange;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Worker config. <br/>
 * TODO: rename to WorkerProperties
 */
@Setter
@Getter
@Validated
@Configuration
@ConfigurationProperties(prefix = "worker")
public class WorkerConfig {

    @Min(1)
    @Max(value = 65_535, message = "Feeds `WHERE ... NOT IN (?)`; <= 65_535 (JDBC param limit); <= 1000 is recommended")
    private int flowExecThreads;

    @Min(1)
    private int perFlowExecThreads;

    @Min(1)
    private int maxPerFlowExecThreads;

    @Min(0)
    private int errorRetries;

    @Min(1)
    private int reactiveExecThreads;

    @Min(20_000)
    private long flinkSubmitTimeoutMills;

    @Min(1_000)
    @Max(24 * 60 * 60 * 1000)
    private long maxShellExecTimeoutMills;

    @NotNull
    @DurationRange(min = "1s", message = "Must be at least 1s, otherwise every no-cron WAITING flow run fails at once")
    private Duration flowWaitTimeout;
}
