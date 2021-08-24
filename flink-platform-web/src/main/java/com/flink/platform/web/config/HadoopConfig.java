package com.flink.platform.web.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.CommonConfigurationKeysPublic;
import org.apache.hadoop.fs.FileSystem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.flink.platform.common.constants.Constant.SLASH;
import static com.flink.platform.common.constants.JobConstant.ROOT_DIR;

/**
 * create hdfs instance
 *
 * @author tiny.wang
 */
@Configuration
@Slf4j
public class HadoopConfig {

    @Value("${hadoop.user}")
    private String username;

    @Value("${hadoop.password}")
    private String password;

    @Value("${hadoop.hdfs-site}")
    private String hdfsSite;

    @Value("${hadoop.core-site}")
    private String coreSite;

    @Value("${hadoop.local.data-dir}")
    private String localDataDir;

    @Bean("fileSystem")
    public FileSystem createFs() throws Exception {
        System.setProperty("HADOOP_USER_NAME", username);
        org.apache.hadoop.conf.Configuration conf = new org.apache.hadoop.conf.Configuration();
        conf.addResource(coreSite);
        conf.addResource(hdfsSite);
        conf.set("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem");

        log.info("=============== hadoop configuration info start. ===============");
        log.info("hadoop conf: {}, size:{}", conf.toString(), conf.size());
        FileSystem fs = FileSystem.newInstance(conf);
        log.info("fs.defaultFS: {}", conf.get(CommonConfigurationKeysPublic.FS_DEFAULT_NAME_KEY));
        log.info("fileSystem scheme: {}", fs.getScheme());
        log.info("fs.hdfs.impl: {}", conf.get("fs.hdfs.impl"));
        log.info("=============== hadoop configuration info end. ===============");
        return fs;
    }

    @Bean("localDataDir")
    public Path createDataDir() {
        String dataDir = ROOT_DIR + SLASH + localDataDir;
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
        return path;
    }
}