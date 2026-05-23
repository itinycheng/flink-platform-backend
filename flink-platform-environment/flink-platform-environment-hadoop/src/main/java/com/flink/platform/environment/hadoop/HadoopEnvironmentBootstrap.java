package com.flink.platform.environment.hadoop;

import com.flink.platform.common.environment.EnvironmentSpec;
import com.flink.platform.environment.EnvironmentRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Registers Hadoop environments at Spring startup by running {@link HadoopDetector} and registering
 * every (HDFS, name) and (YARN, name) it produces under the detected cluster's real identifier
 * (from {@code dfs.nameservices} / {@code yarn.cluster.id}).
 */
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class HadoopEnvironmentBootstrap {

    private final EnvironmentRegistry registry;
    private final YarnClientFactory yarnClientFactory;
    private final HdfsClientFactory hdfsClientFactory;

    @PostConstruct
    public void register() {
        try {
            HadoopDetector.detect().forEach(spec -> {
                registerSpec(spec);
                log.info("Hadoop env detect {}.", spec);
            });
        } catch (Exception e) {
            log.error("Failed to register HadoopEnvironmentBootstrap.", e);
        }
    }

    private void registerSpec(EnvironmentSpec spec) {
        switch (spec.getType()) {
            case YARN -> registry.register(spec, yarnClientFactory);
            case HDFS -> registry.register(spec, hdfsClientFactory);
            default -> log.warn("Unsupported detected env spec: {}", spec);
        }
    }
}
