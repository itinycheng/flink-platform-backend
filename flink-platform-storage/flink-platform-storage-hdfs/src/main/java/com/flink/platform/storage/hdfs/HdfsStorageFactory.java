package com.flink.platform.storage.hdfs;

import com.flink.platform.storage.base.SpringContext2;
import com.flink.platform.storage.base.StorageFactory;
import com.flink.platform.storage.base.StorageSystem;
import com.flink.platform.storage.base.StorageType;
import com.google.auto.service.AutoService;

/**
 * Hello world!
 */
@AutoService(StorageFactory.class)
public class HdfsStorageFactory implements StorageFactory {

    @Override
    public StorageSystem createStorageSystem() {
        try {
            HdfsStorageProperties properties = SpringContext2.getBean(HdfsStorageProperties.class);
            HdfsStorageSystem hdfsStorageSystem = new HdfsStorageSystem(properties);
            hdfsStorageSystem.open();
            return hdfsStorageSystem;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public StorageType getStorageType() {
        return StorageType.HDFS;
    }
}
