package com.flink.platform.web.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Create flink env config instance. */
@Configuration
@Setter
@Getter
public class FlinkConfig {

    private String version;

    private String commandPath;

    private String jarFile;

    private String className;

    private String libDirs;

    @Bean("flink112")
    @ConfigurationProperties(prefix = "flink.sql112")
    public FlinkConfig createFlinkConfig112() {
        return new FlinkConfig();
    }

    @Bean("flink113")
    @ConfigurationProperties(prefix = "flink.sql113")
    public FlinkConfig createFlinkConfig113() {
        return new FlinkConfig();
    }

    @Bean("flink115")
    @ConfigurationProperties(prefix = "flink.sql115")
    public FlinkConfig createFlinkConfig115() {
        return new FlinkConfig();
    }

    @Bean("flink117")
    @ConfigurationProperties(prefix = "flink.sql117")
    public FlinkConfig createFlinkConfig117() {
        return new FlinkConfig();
    }
}
