package com.flink.platform.web.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkerConfigTest {

    @Test
    void bindsTimeoutDurationsFromApplicationYaml() throws IOException {
        var propertySources =
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        var workerConfig = new Binder(ConfigurationPropertySources.from(propertySources))
                .bind("worker", Bindable.of(WorkerConfig.class))
                .orElseThrow(() -> new AssertionError("worker configuration was not bound"));

        assertEquals(Duration.ofMinutes(5), workerConfig.getFlinkSubmissionTimeout());
        assertEquals(Duration.ofHours(8), workerConfig.getMaxShellExecutionTimeout());
    }

    @Test
    void keepsBareMillisecondValuesCompatible() {
        var properties = new MapConfigurationPropertySource(Map.of(
                "worker.flink-submission-timeout", "300000",
                "worker.max-shell-execution-timeout", "28800000"));
        var workerConfig = new Binder(properties)
                .bind("worker", Bindable.of(WorkerConfig.class))
                .orElseThrow(() -> new AssertionError("worker configuration was not bound"));

        assertEquals(Duration.ofMinutes(5), workerConfig.getFlinkSubmissionTimeout());
        assertEquals(Duration.ofHours(8), workerConfig.getMaxShellExecutionTimeout());
    }
}
