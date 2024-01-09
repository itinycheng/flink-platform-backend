package com.flink.platform.web.external;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalFileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Yarn client service. <br>
 * It is difficult to determine whether two hdfs clients correspond to the same cluster.
 */
@Slf4j
@Lazy
@Component
public class YarnClientService {

    @Autowired
    @Qualifier("clusterIdPath")
    private String clusterIdPath;

    private YarnClient yarnClient;

    private FileSystem fileSystem;

    private boolean isMainCluster = true;

    @PostConstruct
    public void initYarnClient() {
        log.info("Init Yarn and FileSystem clients of hadoop corresponding to the current running instance.");
        Configuration conf = HadoopUtil.getHadoopConfiguration();
        yarnClient = YarnClient.createYarnClient();
        yarnClient.init(new YarnConfiguration(conf));
        yarnClient.start();

        try {
            fileSystem = FileSystem.newInstance(conf);
        } catch (Exception e) {
            throw new RuntimeException("create FileSystem failed", e);
        }

        try {
            isMainCluster = clusterIdPath.contains("hdfs") && fileSystem.exists(new Path(clusterIdPath));
        } catch (Exception e) {
            throw new RuntimeException("check cluster id failed");
        }
    }

    public ApplicationReport getApplicationReport(String applicationName) throws Exception {
        ApplicationId applicationId = ApplicationId.fromString(applicationName);
        return yarnClient.getApplicationReport(applicationId);
    }

    public void killApplication(String applicationName) throws Exception {
        ApplicationId applicationId = ApplicationId.fromString(applicationName);
        yarnClient.killApplication(applicationId);
    }

    public void copyIfNewHdfsAndFileChanged(String localFile, String hdfsFile) throws IOException {
        if (isMainCluster) {
            return;
        }

        Path localPath = new Path(localFile);
        Path hdfsPath = new Path(hdfsFile);

        boolean isCopy = true;
        if (fileSystem.exists(hdfsPath)) {
            LocalFileSystem local = FileSystem.getLocal(fileSystem.getConf());
            FileStatus localFileStatus = local.getFileStatus(localPath);
            FileStatus hdfsFileStatus = fileSystem.getFileStatus(hdfsPath);
            isCopy = localFileStatus.getLen() != hdfsFileStatus.getLen()
                    || localFileStatus.getModificationTime() > hdfsFileStatus.getModificationTime();
        }

        if (isCopy) {
            fileSystem.copyFromLocalFile(false, true, localPath, hdfsPath);
        }
    }

    @PreDestroy
    public void destroy() {
        try {
            yarnClient.stop();
        } catch (Exception ignored) {
        }

        try {
            fileSystem.close();
        } catch (Exception ignored) {
        }
    }
}
