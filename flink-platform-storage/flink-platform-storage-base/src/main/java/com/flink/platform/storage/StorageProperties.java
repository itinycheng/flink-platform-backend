package com.flink.platform.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * hdfs storage properties.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    private String type;

    private String username;

    private String storageBasePath;

    private String localBasePath;

    private Map<String, String> properties;
}
