package com.flink.platform.storage.hdfs;

import com.flink.platform.common.util.Preconditions;
import com.flink.platform.storage.base.StorageStatus;
import com.flink.platform.storage.base.StorageSystem;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.HdfsConfiguration;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;

import static com.flink.platform.common.util.DateUtil.DEFAULT_ZONE_ID;

/**
 * hdfs storage system.
 */
@Slf4j
public class HdfsStorageSystem implements StorageSystem {

    private final HdfsStorageProperties properties;
    protected FileSystem fs;

    public HdfsStorageSystem(@Nonnull HdfsStorageProperties properties) {
        this.properties = Preconditions.checkNotNull(properties);
    }

    public void open() throws IOException {
        System.setProperty("HADOOP_USER_NAME", properties.getUsername());
        org.apache.hadoop.conf.Configuration conf = new HdfsConfiguration();
        properties.getProperties().forEach(conf::set);
        log.info("=============== [hadoop configuration info start.] ===============");
        log.info("[hadoop conf]: size:{}, {}", conf.size(), conf);
        log.info("[fs.defaultFS]: {}", conf.get("fs.defaultFS"));
        log.info("[fs.hdfs.impl]: {}", conf.get("fs.hdfs.impl"));
        fs = FileSystem.newInstance(conf);
        log.info("[fileSystem scheme]: {}", fs.getScheme());
        log.info("=============== [hadoop configuration info end.] ===============");
    }

    @Override
    public StorageStatus getFileStatus(String filePath) throws IOException {
        var path = new Path(filePath);
        FileStatus status = fs.getFileStatus(path);

        long modificationTime = status.getModificationTime();
        var instant = Instant.ofEpochMilli(modificationTime);
        var localDateTime = LocalDateTime.ofInstant(instant, DEFAULT_ZONE_ID);
        return StorageStatus.of(status.getLen(), localDateTime);
    }

    @Override
    public void copyToLocalFile(String srcFile, String dstFile, boolean delSrc, boolean overwrite) throws IOException {
        Path srcPath = new Path(srcFile);
        Path dstPath = new Path(dstFile);

        fs.copyToLocalFile(srcPath, dstPath);
    }

    @Override
    public void copyFromLocalFile(String srcFile, String dstFile, boolean delSrc, boolean overwrite)
            throws IOException {
        Path srcPath = new Path(srcFile);
        Path dstPath = new Path(dstFile);

        fs.copyFromLocalFile(delSrc, overwrite, srcPath, dstPath);
    }

    @Override
    public boolean delete(String filePath, boolean recursive) throws IOException {
        Path dstPath = new Path(filePath);
        return fs.delete(dstPath, recursive);
    }

    @Override
    public boolean mkdir(String path) throws IOException {
        return fs.mkdirs(new Path(path));
    }

    @Override
    public boolean exists(String path) throws IOException {
        return fs.exists(new Path(path));
    }

    @Override
    public void close() throws IOException {
        fs.close();
    }
}
