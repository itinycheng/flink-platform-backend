package com.flink.platform.storage.local;

import com.flink.platform.storage.StorageProperties;
import com.flink.platform.storage.hdfs.HdfsStorageSystem;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.hdfs.HdfsConfiguration;

import java.io.IOException;
import java.util.Map;

/**
 * hdfs storage system.
 */
@Slf4j
public class LocalStorageSystem extends HdfsStorageSystem {

    public LocalStorageSystem(@Nonnull StorageProperties properties) {
        super(properties);
    }

    public void open() throws IOException {
        org.apache.hadoop.conf.Configuration conf = new HdfsConfiguration();
        Map<String, String> props = properties.getProperties();
        if (props != null) {
            props.forEach(conf::set);
        }
        log.info("=============== [storage configuration info start.] ===============");
        log.info("[storage type]: {}", properties.getType());
        log.info("[hadoop conf]: size:{}, {}", conf.size(), conf);
        log.info("[fs.defaultFS]: {}", conf.get("fs.defaultFS"));
        log.info("[fs.hdfs.impl]: {}", conf.get("fs.hdfs.impl"));
        fs = FileSystem.newInstanceLocal(conf);
        log.info("[fileSystem scheme]: {}", fs.getScheme());
        log.info("=============== [storage configuration info end.] ===============");
    }
}
