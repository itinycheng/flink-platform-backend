package com.flink.platform.storage;

import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/** Config block under {@code storage.backends.<type>}. */
@Data
public class BackendProperties {

    private @Nullable Map<String, String> local;

    private @Nullable Map<String, String> hdfs;

    private @Nullable S3Properties s3;

    public record S3Properties(
            String bucket,
            String region,
            @Nullable String endpoint,
            boolean pathStyleAccess,
            @Nullable String accessKey,
            @Nullable String secretKey) {}
}
