package com.flink.platform.storage.s3;

import com.flink.platform.storage.StorageProperties;
import com.flink.platform.storage.base.StorageFactory;
import com.flink.platform.storage.base.StorageSystem;
import com.flink.platform.storage.base.StorageType;
import com.google.auto.service.AutoService;

/** Factory for S3 storage system. Discovered via Java {@link java.util.ServiceLoader}. */
@AutoService(StorageFactory.class)
public class S3StorageFactory implements StorageFactory {

    @Override
    public StorageSystem createStorageSystem(StorageProperties properties) {
        try {
            var s3 = new S3StorageSystem(properties);
            s3.open();
            return s3;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public StorageType getStorageType() {
        return StorageType.S3;
    }
}
