package com.flink.platform.storage.base;

/**
 * storage factory.
 */
public interface StorageFactory {
    StorageSystem createStorageSystem();

    StorageType getStorageType();
}
