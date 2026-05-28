package com.flink.platform.storage.hdfs;

import com.flink.platform.common.util.Preconditions;
import com.flink.platform.storage.StorageProperties;
import com.flink.platform.storage.base.StorageStatus;
import com.flink.platform.storage.base.StorageSystem;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalFileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.Trash;
import org.apache.hadoop.hdfs.HdfsConfiguration;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;

import static com.flink.platform.common.constants.Constant.GLOBAL_ZONE_ID;
import static com.flink.platform.common.constants.Constant.SLASH;
import static com.flink.platform.common.util.StringUtil.stripLeadingSlash;

/**
 * hdfs storage system.
 */
@Slf4j
public class HdfsStorageSystem implements StorageSystem {

    protected final StorageProperties properties;
    protected FileSystem fs;
    protected String rootPath;

    public HdfsStorageSystem(@Nonnull StorageProperties properties) {
        this.properties = Preconditions.checkNotNull(properties);
    }

    @Override
    public String getRootPath() {
        return rootPath;
    }

    @Override
    public boolean isDistributed() {
        return true;
    }

    @Override
    public String getFileSeparator() {
        return SLASH;
    }

    @Override
    public String getParentPath(String filePath) {
        return new Path(filePath).getParent().toString();
    }

    @Override
    public StorageStatus getFileStatus(String filePath) throws IOException {
        var path = new Path(filePath);
        FileStatus status = fs.getFileStatus(path);

        var modificationTime = status.getModificationTime();
        var instant = Instant.ofEpochMilli(modificationTime);
        var localDateTime = LocalDateTime.ofInstant(instant, GLOBAL_ZONE_ID);
        return StorageStatus.of(status.getLen(), localDateTime);
    }

    @Override
    public void copyToLocalFile(String srcFile, String dstFile) throws IOException {
        Path srcPath = new Path(srcFile);
        Path dstPath = new Path(dstFile);

        fs.copyToLocalFile(srcPath, dstPath);
    }

    @Override
    public void copyToLocalFileIfChanged(String hdfsFile, String localFile) throws IOException {
        Path hdfsPath = new Path(hdfsFile);
        Path localPath = new Path(localFile);

        boolean isCopy = true;
        LocalFileSystem local = FileSystem.getLocal(fs.getConf());
        if (local.exists(localPath)) {
            FileStatus localFileStatus = local.getFileStatus(localPath);
            FileStatus hdfsFileStatus = fs.getFileStatus(hdfsPath);
            isCopy = localFileStatus.getLen() != hdfsFileStatus.getLen()
                    || localFileStatus.getModificationTime() < hdfsFileStatus.getModificationTime();
        }
        if (isCopy) {
            fs.copyToLocalFile(hdfsPath, localPath);
        }
    }

    @Override
    public void copyFromLocalFile(String srcFile, String dstFile, boolean delSrc, boolean overwrite)
            throws IOException {
        Path srcPath = new Path(srcFile);
        Path dstPath = new Path(dstFile);

        fs.copyFromLocalFile(delSrc, overwrite, srcPath, dstPath);
    }

    @Override
    public void createFile(String file, String data, boolean overwrite) throws IOException {
        Path filePath = new Path(file);
        try (FSDataOutputStream out = fs.create(filePath, overwrite)) {
            out.writeBytes(data);
        }
    }

    @Override
    public boolean delete(String filePath, boolean recursive) throws IOException {
        Path dstPath = new Path(filePath);
        return fs.delete(dstPath, recursive);
    }

    @Override
    public boolean moveToTrash(String path) throws IOException {
        return Trash.moveToAppropriateTrash(fs, new Path(path), fs.getConf());
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
    public boolean rename(String srcPath, String dstPath) throws IOException {
        Path src = new Path(srcPath);
        Path dst = new Path(dstPath);
        return fs.rename(src, dst);
    }

    /**
     * overwrite this method when extends by an object storage system.
     */
    @Override
    public String normalizePath(String path) {
        var uri = fs.makeQualified(new Path(path)).toUri();
        return uri.getScheme() + ":" + uri.getPath();
    }

    @Override
    public boolean isAbsolutePath(String path) {
        Path hdfsPath = new Path(path);
        return hdfsPath.isAbsolute();
    }

    // ==================================================================
    // ========================= init and close =========================
    // ==================================================================

    @Override
    public void open() throws IOException {
        var hdfsProperties = properties.getHdfsProperties();
        var username = hdfsProperties.get("hadoop.username");
        if (StringUtils.isNotBlank(username)) {
            System.setProperty("HADOOP_USER_NAME", username.trim());
        }

        org.apache.hadoop.conf.Configuration conf = new HdfsConfiguration();
        hdfsProperties.forEach(conf::set);
        fs = FileSystem.newInstance(conf);
        // create and get root path.
        rootPath = initializeRootPath();
        // Log configuration information.
        StringBuilder builder = new StringBuilder();
        builder.append("=============== [storage configuration info start.] ===============\n");
        builder.append("[hdfs conf size]: ").append(conf.size()).append("\n");
        builder.append("[hdfs uri]: ").append(fs.getUri()).append("\n");
        builder.append("[fs.defaultFS]: ").append(conf.get("fs.defaultFS")).append("\n");
        builder.append("[fs.hdfs.impl]: ").append(conf.get("fs.hdfs.impl")).append("\n");
        builder.append("[fileSystem scheme]: ").append(fs.getScheme()).append("\n");
        builder.append(conf).append("\n");
        builder.append("=============== [storage configuration info end.] ===============");
        log.info("Hdfs FileSystem initialized successfully.\n{}", builder);
    }

    @Override
    public void close() throws IOException {
        fs.close();
    }

    /**
     * basePath is always treated as absolute under the HDFS namespace root, so "flink-platform" and
     * "/flink-platform" both resolve to <fs.defaultFS>/flink-platform.
     */
    private String initializeRootPath() throws IOException {
        var absolutePath = SLASH + stripLeadingSlash(properties.getBasePath());
        var path = new Path(absolutePath);
        if (fs.exists(path)) {
            log.info("storage base dir: {} already exists", absolutePath);
        } else if (fs.mkdirs(path)) {
            log.info("storage base dir: {} created successfully.", absolutePath);
        } else {
            throw new RuntimeException("create storage base dir: %s failed.".formatted(absolutePath));
        }

        return normalizePath(absolutePath);
    }
}
