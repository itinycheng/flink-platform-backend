package com.flink.platform.web.environment;

import com.flink.platform.common.environment.EnvironmentType;
import com.flink.platform.environment.EnvironmentRegistry;
import com.flink.platform.web.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;

/** HDFS-backed implementation of {@link EnvironmentFileAdapter}. */
@Slf4j
@Component
@Order(1)
@SuppressWarnings("resource")
@DependsOn("hadoopEnvironmentBootstrap")
public class HdfsFileAdapter extends EnvironmentFileAdapter {

    private static final String HDFS_SCHEME_PREFIX = "hdfs:";

    private final StorageService storageService;

    @Autowired
    public HdfsFileAdapter(
            @Qualifier("primaryClusterIdFilePath") String primaryClusterIdFilePath,
            EnvironmentRegistry registry,
            StorageService storageService) {
        super(registry, primaryClusterIdFilePath);
        this.storageService = storageService;
    }

    @Override
    public EnvironmentType supportedType() {
        return EnvironmentType.HDFS;
    }

    private FileSystem hdfsClient() {
        return registry.getClient(EnvironmentType.HDFS);
    }

    @Override
    protected boolean checkOnPrimaryCluster(String primaryClusterIdFilePath) throws Exception {
        return primaryClusterIdFilePath.contains(HDFS_SCHEME_PREFIX)
                && hdfsClient().exists(new Path(primaryClusterIdFilePath));
    }

    @Override
    protected void doCopyIfChanged(String localFile, String remoteFile) throws IOException {
        var localPath = new Path(localFile);
        var hdfsPath = new Path(remoteFile);
        var client = hdfsClient();

        boolean isCopy = true;
        if (client.exists(hdfsPath)) {
            var local = FileSystem.getLocal(client.getConf());
            var localFileStatus = local.getFileStatus(localPath);
            var hdfsFileStatus = client.getFileStatus(hdfsPath);
            isCopy = localFileStatus.getLen() != hdfsFileStatus.getLen()
                    || localFileStatus.getModificationTime() > hdfsFileStatus.getModificationTime();
        }

        if (isCopy) {
            client.copyFromLocalFile(false, true, localPath, hdfsPath);
        }
    }

    @Override
    public void writeToFilePath(String filePath, String content) throws IOException {
        var path = new Path(filePath);
        try (var out = hdfsClient().create(path, true)) {
            out.write(content.getBytes(UTF_8));
        }
    }

    @Override
    public String buildTempPath(String... segments) {
        var storageRoot = storageService.getRootPath();
        if (!storageRoot.startsWith(HDFS_SCHEME_PREFIX)) {
            throw new IllegalStateException(
                    "HDFS dispatch tmp path requires storage on HDFS; current storage root: " + storageRoot);
        }
        var base = storageRoot + "/tmp";
        return segments.length == 0 ? base : base + "/" + String.join("/", segments);
    }
}
