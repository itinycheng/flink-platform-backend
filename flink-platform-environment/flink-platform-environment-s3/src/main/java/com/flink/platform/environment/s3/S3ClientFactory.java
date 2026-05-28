package com.flink.platform.environment.s3;

import com.flink.platform.common.environment.EnvironmentSpec;
import com.flink.platform.common.environment.EnvironmentType;
import com.flink.platform.environment.EnvironmentClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Builds {@link S3Client} for S3-typed environments.
 */
@Slf4j
@Component
public class S3ClientFactory implements EnvironmentClientFactory<S3Client> {

    @Override
    public EnvironmentType supportedType() {
        return EnvironmentType.S3;
    }

    @Override
    public S3Client create(EnvironmentSpec spec) {
        var client = S3Client.builder().build();
        log.info("S3Client created for env spec: {}", spec);
        return client;
    }

    @Override
    public boolean healthy(S3Client client) {
        try {
            client.listBuckets();
            return true;
        } catch (Exception e) {
            log.warn("S3Client health probe failed: {}", e.toString());
            return false;
        }
    }
}
