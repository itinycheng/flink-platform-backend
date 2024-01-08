package com.flink.platform.storage.hdfs;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * hdfs storage properties.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "hadoop")
public class HdfsStorageProperties {
    private String username;

    private String localDirName;

    private String hdfsFilePath;

    private Map<String, String> properties;
}
