package com.flink.platform.storage.base;

import com.flink.platform.storage.StorageProperties;

/**
 * storage factory.
 */
public interface StorageFactory {
    StorageSystem createStorageSystem(StorageProperties properties);

    StorageType getStorageType();
}
