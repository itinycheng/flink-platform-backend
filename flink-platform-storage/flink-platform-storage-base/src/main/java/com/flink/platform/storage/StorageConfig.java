package com.flink.platform.storage;

import com.flink.platform.storage.base.StorageFactory;
import com.flink.platform.storage.base.StorageSystem;
import com.flink.platform.storage.base.StorageType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ServiceLoader;
import java.util.UUID;

/**
 * storage loader.
 */
@SuppressWarnings("unused")
@Slf4j
@Configuration
public class StorageConfig {

    @Bean
    public StorageSystem createStorageSystem(StorageProperties properties) {
        StorageType storageType = StorageType.from(properties.getType());
        ServiceLoader<StorageFactory> storageFactories = ServiceLoader.load(StorageFactory.class);
        for (StorageFactory storageFactory : storageFactories) {
            if (storageFactory.getStorageType().equals(storageType)) {
                return storageFactory.createStorageSystem(properties);
            }
        }

        throw new IllegalStateException("No storage factory found for type: " + storageType);
    }

    /**
     * Multi-node init may race, last-writer-wins is acceptable.
     */
    @Bean("primaryClusterIdFilePath")
    public String primaryClusterIdFilePath(StorageSystem storageSystem, StorageProperties properties) throws Exception {
        String storageBasePath = properties.getBasePath();
        String fileSeparator = storageSystem.getFileSeparator();
        String clusterIdFile = String.join(fileSeparator, storageBasePath, ".main_cluster_id");

        if (!storageSystem.exists(clusterIdFile)) {
            var clusterId = UUID.randomUUID().toString();
            storageSystem.createFile(clusterIdFile, clusterId, true);
            log.info("Primary cluster ID file created at {}, content: {}", clusterIdFile, clusterId);
        }

        return storageSystem.normalizePath(clusterIdFile);
    }
}
