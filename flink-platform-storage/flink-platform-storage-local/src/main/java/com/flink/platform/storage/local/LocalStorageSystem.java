package com.flink.platform.storage.local;

import com.flink.platform.storage.hdfs.HdfsStorageProperties;
import com.flink.platform.storage.hdfs.HdfsStorageSystem;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.hdfs.HdfsConfiguration;

import java.io.IOException;

/**
 * hdfs storage system.
 */
@Slf4j
public class LocalStorageSystem extends HdfsStorageSystem {

    private final LocalStorageProperties properties;

    public LocalStorageSystem(@Nonnull LocalStorageProperties properties) {
        super(new HdfsStorageProperties());
        this.properties = properties;
    }

    public void open() throws IOException {
        System.setProperty("HADOOP_USER_NAME", properties.getUsername());
        org.apache.hadoop.conf.Configuration conf = new HdfsConfiguration();
        properties.getProperties().forEach(conf::set);
        log.info("=============== [hadoop configuration info start.] ===============");
        log.info("[hadoop conf]: size:{}, {}", conf.size(), conf);
        log.info("[fs.defaultFS]: {}", conf.get("fs.defaultFS"));
        log.info("[fs.hdfs.impl]: {}", conf.get("fs.hdfs.impl"));
        fs = FileSystem.newInstanceLocal(conf);
        log.info("[fileSystem scheme]: {}", fs.getScheme());
        log.info("=============== [hadoop configuration info end.] ===============");
    }
}
