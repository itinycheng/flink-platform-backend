package com.flink.platform.environment.s3;

import com.flink.platform.common.environment.EnvironmentSpec;
import com.flink.platform.common.environment.EnvironmentType;
import com.flink.platform.common.util.S3Util;
import com.flink.platform.environment.EnvironmentClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static software.amazon.awssdk.http.HttpStatusCode.BAD_REQUEST;
import static software.amazon.awssdk.http.HttpStatusCode.INTERNAL_SERVER_ERROR;

/**
 * Builds {@link S3Client} for S3-typed environments.
 */
@Slf4j
@Component
public class S3ClientFactory implements EnvironmentClientFactory<S3Client> {

    private static final String HEALTH_PROBE_BUCKET = "INVALID-HEALTH-PROBE-BUCKET";

    @Override
    public EnvironmentType supportedType() {
        return EnvironmentType.S3;
    }

    @Override
    public S3Client create(EnvironmentSpec spec) {
        var enabled = S3Util.shouldForcePathStyle();
        var client = S3Client.builder().forcePathStyle(enabled).build();
        log.info("S3Client created for env spec: {}", spec);
        return client;
    }

    /**
     * Probes the S3 endpoint with a HeadBucket against a bucket name that cannot exist.
     *
     * <p>A 4xx response means the service is reachable and credentials work; only network
     * failures or 5xx are treated as unhealthy. Avoids requiring {@code s3:ListAllMyBuckets} (which
     * is account-level and rarely granted under least-privilege IAM).
     *
     * <p>Refer to: {@link software.amazon.awssdk.http.HttpStatusCode}
     */
    @Override
    public boolean healthy(S3Client client) {
        try {
            client.headBucket(
                    HeadBucketRequest.builder().bucket(HEALTH_PROBE_BUCKET).build());
            return true;
        } catch (S3Exception e) {
            return e.statusCode() >= BAD_REQUEST && e.statusCode() < INTERNAL_SERVER_ERROR;
        } catch (Exception e) {
            log.warn("S3Client health probe failed", e);
            return false;
        }
    }
}
