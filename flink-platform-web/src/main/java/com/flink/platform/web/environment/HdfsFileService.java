package com.flink.platform.web.environment;

import com.flink.platform.common.environment.EnvironmentType;
import com.flink.platform.environment.EnvironmentRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Cross-cluster HDFS file operations: ensures resources from the primary HDFS are present on the
 * local cluster's HDFS before a job submission, and supports direct file writes. Consumes a
 * {@link FileSystem} from {@link EnvironmentRegistry}.
 */
@Slf4j
@Lazy
@Component
@DependsOn("hadoopEnvironmentBootstrap")
public class HdfsFileService {

    private final String primaryClusterIdFilePath;

    private final EnvironmentRegistry registry;

    private volatile boolean isPrimaryCluster = true;

    @Autowired
    public HdfsFileService(
            @Qualifier("primaryClusterIdFilePath") String primaryClusterIdFilePath, EnvironmentRegistry registry) {
        this.primaryClusterIdFilePath = primaryClusterIdFilePath;
        this.registry = registry;
    }

    @PostConstruct
    public void initPrimaryCluster() {
        try {
            isPrimaryCluster = primaryClusterIdFilePath.contains("hdfs")
                    && hdfsClient().exists(new Path(primaryClusterIdFilePath));
        } catch (Exception e) {
            throw new RuntimeException("check cluster id failed");
        }
    }

    private FileSystem hdfsClient() {
        return registry.getClient(EnvironmentType.HDFS);
    }

    public void copyIfNewHdfsAndFileChanged(String localFile, String hdfsFile) throws IOException {
        if (isPrimaryCluster) {
            return;
        }

        var localPath = new Path(localFile);
        var hdfsPath = new Path(hdfsFile);
        var hdfsClient = hdfsClient();

        boolean isCopy = true;
        if (hdfsClient.exists(hdfsPath)) {
            var local = FileSystem.getLocal(hdfsClient.getConf());
            var localFileStatus = local.getFileStatus(localPath);
            var hdfsFileStatus = hdfsClient.getFileStatus(hdfsPath);
            isCopy = localFileStatus.getLen() != hdfsFileStatus.getLen()
                    || localFileStatus.getModificationTime() > hdfsFileStatus.getModificationTime();
        }

        if (isCopy) {
            hdfsClient.copyFromLocalFile(false, true, localPath, hdfsPath);
        }
    }

    public void writeToFilePath(String filePath, String content) throws IOException {
        var path = new Path(filePath);
        try (var out = hdfsClient().create(path, true)) {
            out.write(content.getBytes(UTF_8));
        }
    }
}
