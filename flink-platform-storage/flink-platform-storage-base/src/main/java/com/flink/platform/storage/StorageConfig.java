package com.flink.platform.storage;

import com.flink.platform.storage.base.StorageFactory;
import com.flink.platform.storage.base.StorageSystem;
import com.flink.platform.storage.base.StorageType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ServiceLoader;
import java.util.UUID;

import static com.flink.platform.common.constants.Constant.FILE_SEPARATOR;
import static com.flink.platform.common.constants.Constant.ROOT_DIR;
import static com.flink.platform.common.constants.Constant.SLASH;
import static java.lang.String.format;

/**
 * storage loader.
 */
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

        return null;
    }

    @Bean("localBasePath")
    public String createLocalBasePath(StorageProperties properties) {
        String dataDir = ROOT_DIR + SLASH + properties.getLocalBasePath();
        Path path = Paths.get(dataDir);
        File file = path.toFile();
        if (!file.exists()) {
            if (file.mkdir()) {
                log.info("data dir: {} created successfully.", dataDir);
            } else {
                throw new RuntimeException("create local data dir failed.");
            }
        } else {
            log.info("data dir: {} already exists", dataDir);
        }
        return path.toString();
    }

    @Bean("storageBasePath")
    public String createStorageBasePath(StorageSystem storageSystem, StorageProperties properties) throws Exception {
        String storageBasePath = properties.getStorageBasePath();
        if (!storageSystem.exists(storageBasePath)) {
            if (storageSystem.mkdir(storageBasePath)) {
                log.info("storage base dir: {} created successfully.", storageBasePath);
            } else {
                throw new RuntimeException(format("create storage base dir: %s failed.", storageBasePath));
            }
        } else {
            log.info("storage base dir: {} already exists", storageBasePath);
        }

        return storageSystem.normalizePath(storageBasePath);
    }

    @Bean("clusterIdPath")
    public String createClusterId(StorageSystem storageSystem, StorageProperties properties) throws Exception {
        String storageBasePath = properties.getStorageBasePath();
        String clusterIdFile = String.join(FILE_SEPARATOR, storageBasePath, ".main_cluster_id");
        if (!storageSystem.exists(clusterIdFile)) {
            String clusterId = UUID.randomUUID().toString();
            String tmpClusterIdFile = String.join(FILE_SEPARATOR, storageBasePath, ".main_cluster_id" + clusterId);

            storageSystem.createFile(tmpClusterIdFile, clusterId, true);
            if (!storageSystem.exists(clusterIdFile)) {
                storageSystem.rename(tmpClusterIdFile, clusterIdFile);
            }
        }

        return storageSystem.normalizePath(clusterIdFile);
    }
}
