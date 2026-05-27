package com.flink.platform.storage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/** Config block under {@code storage.backends.<type>}. */
@Data
public class BackendProperties {

    private @Nullable LocalProperties local;

    private @Nullable Map<String, String> hdfs;

    @Valid
    private @Nullable S3Properties s3;

    public record LocalProperties(@NotBlank String workDir) {}

    public record S3Properties(
            @NotBlank String bucket,
            @NotBlank String region,
            @Nullable String endpoint,
            boolean pathStyleAccess,
            @Nullable String accessKey,
            @Nullable String secretKey) {}
}
