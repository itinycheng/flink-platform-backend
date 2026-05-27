package com.flink.platform.storage.local;

import com.flink.platform.common.constants.Constant;
import com.flink.platform.storage.StorageProperties;
import com.flink.platform.storage.hdfs.HdfsStorageSystem;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.HdfsConfiguration;

import java.io.IOException;
import java.nio.file.Paths;

import static com.flink.platform.common.constants.Constant.TMP_DIR;
import static com.flink.platform.common.util.StringUtil.stripLeadingSlash;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Local filesystem storage system.
 */
@Slf4j
public class LocalStorageSystem extends HdfsStorageSystem {

    public LocalStorageSystem(@Nonnull StorageProperties properties) {
        super(properties);
    }

    @Override
    public void open() throws IOException {
        var conf = new HdfsConfiguration();
        log.info("=============== [storage configuration info start.] ===============");
        log.info("[storage type]: {}", properties.getType());
        log.info("[hadoop conf]: size:{}, {}", conf.size(), conf);
        log.info("[fs.defaultFS]: {}", conf.get("fs.defaultFS"));
        log.info("[fs.hdfs.impl]: {}", conf.get("fs.hdfs.impl"));
        fs = FileSystem.newInstanceLocal(conf);
        rootPath = initLocalRootPath();
        log.info("[fileSystem scheme]: {}", fs.getScheme());
        log.info("=============== [storage configuration info end.] ===============");
    }

    @Override
    public boolean isDistributed() {
        return false;
    }

    @Override
    public String getFileSeparator() {
        return Constant.OS_FILE_SEPARATOR;
    }

    private String initLocalRootPath() throws IOException {
        var local = properties.getLocalProperties();
        var workDir = local.workDir();
        workDir = isNotBlank(workDir) ? workDir : TMP_DIR;

        var basePath = stripLeadingSlash(properties.getBasePath());
        var absolutePath = Paths.get(workDir, basePath).toString();
        var path = new Path(absolutePath);
        if (!fs.exists(path) && !fs.mkdirs(path)) {
            throw new RuntimeException("create storage base dir: %s failed.".formatted(path));
        }

        return normalizePath(absolutePath);
    }
}
