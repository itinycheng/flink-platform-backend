package com.flink.platform.storage.local;

import com.flink.platform.storage.StorageProperties;
import com.flink.platform.storage.base.StorageFactory;
import com.flink.platform.storage.base.StorageSystem;
import com.flink.platform.storage.base.StorageType;
import com.google.auto.service.AutoService;

/**
 * Hello world!
 */
@AutoService(StorageFactory.class)
public class LocalStorageFactory implements StorageFactory {

    @Override
    public StorageSystem createStorageSystem(StorageProperties properties) {
        try {
            LocalStorageSystem localStorageSystem = new LocalStorageSystem(properties);
            localStorageSystem.open();
            return localStorageSystem;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public StorageType getStorageType() {
        return StorageType.LOCAL;
    }
}
