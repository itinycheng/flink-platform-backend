package com.flink.platform.environment.s3;

import com.flink.platform.common.environment.EnvironmentSpec;
import com.flink.platform.common.environment.EnvironmentType;
import com.flink.platform.environment.EnvironmentClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.utils.SystemSetting;

import java.util.Optional;

import static software.amazon.awssdk.profiles.ProfileFileSystemSetting.AWS_PROFILE;

/**
 * Builds {@link S3Client} for S3-typed environments.
 */
@Slf4j
@Component
public class S3ClientFactory implements EnvironmentClientFactory<S3Client> {

    private static final SystemSetting ENDPOINT_S3 =
            new CustomSystemSetting("aws.endpointUrlS3", "AWS_ENDPOINT_URL_S3");
    private static final SystemSetting ENDPOINT_GLOBAL = new CustomSystemSetting("aws.endpointUrl", "AWS_ENDPOINT_URL");

    /**
     * Project-defined names: AWS SDK v2 has no env/system-property for forcePathStyle (it lives
     * only onS3Configuration#pathStyleAccessEnabled).
     */
    private static final SystemSetting FORCE_PATH_STYLE =
            new CustomSystemSetting("aws.s3.forcePathStyle", "AWS_S3_FORCE_PATH_STYLE");

    @Override
    public EnvironmentType supportedType() {
        return EnvironmentType.S3;
    }

    @Override
    public S3Client create(EnvironmentSpec spec) {
        var enabled = FORCE_PATH_STYLE.getBooleanValue().orElseGet(this::isNonAwsEndpoint);
        var client = S3Client.builder().forcePathStyle(enabled).build();
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

    // ====================================================
    // ====================== private =====================
    // ====================================================

    private boolean isNonAwsEndpoint() {
        return ENDPOINT_S3
                .getStringValue()
                .or(ENDPOINT_GLOBAL::getStringValue)
                .or(this::endpointFromProfile)
                .filter(s -> !s.isBlank())
                .map(s -> !s.contains("amazonaws.com"))
                .orElse(false);
    }

    private Optional<String> endpointFromProfile() {
        var profileName = AWS_PROFILE.getStringValue().orElse("default");
        return ProfileFile.defaultProfileFile().profile(profileName).flatMap(p -> p.property("endpoint_url"));
    }

    private record CustomSystemSetting(String property, String environmentVariable) implements SystemSetting {

        @Override
        @Nullable
        public String defaultValue() {
            return null;
        }
    }
}
