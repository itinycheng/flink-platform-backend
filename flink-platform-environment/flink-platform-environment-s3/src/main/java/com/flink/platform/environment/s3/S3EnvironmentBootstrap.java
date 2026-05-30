package com.flink.platform.environment.s3;

import com.flink.platform.common.environment.EnvironmentSpec;
import com.flink.platform.common.environment.EnvironmentType;
import com.flink.platform.environment.EnvironmentRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;

/**
 * Registers a single S3 environment at Spring startup.
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
        if (!hasResolvableCredentials()) {
            log.info("AWS credentials not resolvable in node context; skipping S3 env registration.");
            return;
        }

        var spec = new EnvironmentSpec(EnvironmentType.S3, DEFAULT_S3_NAME);
        registry.register(spec, s3ClientFactory);
        log.info("S3 env registered: {}", spec);
    }

    private boolean hasResolvableCredentials() {
        try (var provider = DefaultCredentialsProvider.builder().build()) {
            provider.resolveCredentials();
            return true;
        } catch (SdkClientException e) {
            log.debug("AWS credentials lookup failed: {}", e.toString());
            return false;
        }
    }
}
