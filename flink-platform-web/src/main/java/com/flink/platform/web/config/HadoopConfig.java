package com.flink.platform.web.config;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

import static com.flink.platform.common.constants.Constant.ROOT_DIR;
import static com.flink.platform.common.constants.Constant.SLASH;

/** create hdfs instance where to store upload resources. */
@Slf4j
@Setter
@Configuration
@ConfigurationProperties(prefix = "hadoop")
public class HadoopConfig {

    private String username;

    private String localDirName;

    private String hdfsFilePath;

    private Map<String, String> properties;

    @Lazy
    @Bean("hdfsFileSystem")
    public FileSystem createHdfsFileSystem() throws Exception {
        System.setProperty("HADOOP_USER_NAME", username);
        org.apache.hadoop.conf.Configuration conf = new org.apache.hadoop.conf.Configuration();
        properties.forEach(conf::set);
        log.info("=============== [hadoop configuration info start.] ===============");
        log.info("[hadoop conf]: size:{}, {}", conf.size(), conf);
        log.info("[fs.defaultFS]: {}", conf.get("fs.defaultFS"));
        log.info("[fs.hdfs.impl]: {}", conf.get("fs.hdfs.impl"));
        FileSystem fs = FileSystem.newInstance(conf);
        log.info("[fileSystem scheme]: {}", fs.getScheme());
        log.info("=============== [hadoop configuration info end.] ===============");
        return fs;
    }

    @Bean("localDataDir")
    public String createDataDir() {
        String dataDir = ROOT_DIR + SLASH + localDirName;
        Path path = Paths.get(dataDir);
        File file = path.toFile();
        if (!file.exists()) {
            if (file.mkdir()) {
                log.info("data dir: {} created successfully.", dataDir);
            } else {
                throw new RuntimeException("create local data dir failed.");
            }
        } else {
            log.info("data dir: {} already exists", dataDir);
        }
        return path.toString();
    }

    @Lazy
    @Bean("projectHdfsPath")
    public String createHdfsFilePath(FileSystem hdfsFileSystem) throws Exception {
        org.apache.hadoop.fs.Path path = new org.apache.hadoop.fs.Path(hdfsFilePath);
        if (!hdfsFileSystem.exists(path)) {
            if (hdfsFileSystem.mkdirs(path)) {
                log.info("hdfs file dir: {} created successfully.", hdfsFilePath);
            } else {
                throw new RuntimeException("create hdfs file dir failed.");
            }
        } else {
            log.info("hdfs file dir: {} already exists", hdfsFilePath);
        }
        return path.toString();
    }

    @Lazy
    @Bean("hdfsClusterIdPath")
    public String createHdfsClusterId(
            @Qualifier("hdfsFileSystem") FileSystem hdfsFileSystem,
            @Qualifier("projectHdfsPath") String projectHdfsPath)
            throws Exception {
        org.apache.hadoop.fs.Path parent = new org.apache.hadoop.fs.Path(projectHdfsPath);
        org.apache.hadoop.fs.Path clusterIdFile = new org.apache.hadoop.fs.Path(parent, ".main_cluster_id");

        if (!hdfsFileSystem.exists(clusterIdFile)) {
            String clusterId = UUID.randomUUID().toString();
            org.apache.hadoop.fs.Path tmpClusterIdFile =
                    new org.apache.hadoop.fs.Path(parent, ".main_cluster_id." + clusterId);

            try (FSDataOutputStream out = hdfsFileSystem.create(tmpClusterIdFile, true)) {
                out.writeBytes(UUID.randomUUID().toString());
            } catch (Exception e) {
                throw new RuntimeException("create cluster id file failed.", e);
            }

            if (!hdfsFileSystem.exists(clusterIdFile)) {
                hdfsFileSystem.rename(tmpClusterIdFile, clusterIdFile);
            }
        }

        return clusterIdFile.toString();
    }
}
