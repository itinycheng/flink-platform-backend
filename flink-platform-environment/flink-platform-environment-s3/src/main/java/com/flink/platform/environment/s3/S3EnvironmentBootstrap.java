package com.flink.platform.environment.s3;

import com.flink.platform.common.environment.EnvironmentSpec;
import com.flink.platform.common.environment.EnvironmentType;
import com.flink.platform.environment.EnvironmentRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Unconditionally registers a single S3 environment at Spring startup. The actual credential and
 * endpoint resolution is deferred to {@link S3ClientFactory}, which delegates to the AWS SDK
 * default chain. The client is built lazily on first use, and health checks invalidate it if
 * resolution ultimately fails.
 */
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class S3EnvironmentBootstrap {

    private static final String DEFAULT_S3_NAME = "default";

    private final EnvironmentRegistry registry;
    private final S3ClientFactory s3ClientFactory;

    @PostConstruct
    public void register() {
        var spec = new EnvironmentSpec(EnvironmentType.S3, DEFAULT_S3_NAME);
        registry.register(spec, s3ClientFactory);
        log.info("S3 env registered: {}", spec);
    }
}
