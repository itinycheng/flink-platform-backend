package com.flink.platform.common.util;

import com.flink.platform.common.model.S3Location;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.utils.SystemSetting;

import java.util.Optional;

import static com.flink.platform.common.constants.Constant.SLASH;
import static software.amazon.awssdk.profiles.ProfileFileSystemSetting.AWS_PROFILE;

/**
 * s3 utils.
 */
public class S3Util {

    public static final String S3_SCHEME_PREFIX = "s3://";

    public static final String S3A_SCHEME_PREFIX = "s3a://";

    private static final SystemSetting ENDPOINT_S3 =
            new CustomSystemSetting("aws.endpointUrlS3", "AWS_ENDPOINT_URL_S3");
    private static final SystemSetting ENDPOINT_GLOBAL = new CustomSystemSetting("aws.endpointUrl", "AWS_ENDPOINT_URL");

    /**
     * Project-defined names: AWS SDK v2 has no env/system-property for forcePathStyle (it lives
     * only onS3Configuration#pathStyleAccessEnabled).
     */
    private static final SystemSetting FORCE_PATH_STYLE =
            new CustomSystemSetting("aws.s3.forcePathStyle", "AWS_S3_FORCE_PATH_STYLE");

    public static S3Location toLocation(String pathStr) {
        if (pathStr == null) {
            throw new IllegalArgumentException("path is null");
        }

        String stripped;
        if (pathStr.startsWith(S3_SCHEME_PREFIX)) {
            stripped = pathStr.substring(S3_SCHEME_PREFIX.length());
        } else if (pathStr.startsWith(S3A_SCHEME_PREFIX)) {
            stripped = pathStr.substring(S3A_SCHEME_PREFIX.length());
        } else {
            throw new IllegalArgumentException("not an S3 path: " + pathStr);
        }

        int idx = stripped.indexOf(SLASH);
        String bucket = idx < 0 ? stripped : stripped.substring(0, idx);
        String key = idx < 0 ? "" : stripped.substring(idx + 1);
        return new S3Location(bucket, key);
    }

    public static boolean shouldForcePathStyle() {
        // Explicit override (AWS_S3_FORCE_PATH_STYLE) wins; otherwise infer from custom endpoint:
        // non-empty + non-amazonaws → S3-compatible service (MinIO/SeaweedFS), needs path-style.
        Optional<Boolean> booleanValue = FORCE_PATH_STYLE.getBooleanValue();
        if (booleanValue.isPresent()) {
            return booleanValue.get();
        }

        Optional<String> endpoint = ENDPOINT_S3.getStringValue();
        if (!endpoint.isPresent()) {
            endpoint = ENDPOINT_GLOBAL.getStringValue();
        }

        // Falls back to ~/.aws/config + ~/.aws/credentials (profile-level endpoint_url only;
        // services-block syntax not supported — use AWS_S3_FORCE_PATH_STYLE to override).
        if (!endpoint.isPresent()) {
            String profileName = AWS_PROFILE.getStringValue().orElse("default");
            endpoint = ProfileFile.defaultProfileFile().profile(profileName).flatMap(p -> p.property("endpoint_url"));
        }

        if (!endpoint.isPresent()) {
            return false;
        }

        String value = endpoint.get();
        return !value.trim().isEmpty() && !value.contains("amazonaws.com");
    }
}
