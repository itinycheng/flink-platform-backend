package com.flink.platform.storage;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

/**
 * Storage properties.
 *
 * <p>Schema: {@code storage.type} selects the active backend; {@code storage.backends.<type>}
 * holds per-backend settings. All backend blocks may co-exist in one file; only the one matching
 * {@code type} is loaded at runtime.
 */
@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "storage")
@SuppressWarnings("NullAway.Init")
public class StorageProperties {

    @NotBlank
    private String type;

    @NotBlank
    private String basePath;

    private BackendProperties backends = new BackendProperties();

    public Map<String, String> getLocalProperties() {
        var local = backends.getLocal();
        return local != null ? local : new HashMap<>();
    }

    public Map<String, String> getHdfsProperties() {
        var hdfs = backends.getHdfs();
        return hdfs != null ? hdfs : new HashMap<>();
    }

    public BackendProperties.S3Properties getS3Properties() {
        var s3 = backends.getS3();
        if (s3 == null) {
            throw new IllegalStateException("S3 properties must be provided when storage type is s3");
        }
        return s3;
    }
}
