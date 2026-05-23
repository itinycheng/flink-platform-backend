package com.flink.platform.environment.hadoop;

import com.flink.platform.common.environment.EnvironmentSpec;
import com.flink.platform.common.environment.EnvironmentType;
import com.flink.platform.environment.EnvironmentClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FileSystem;
import org.springframework.stereotype.Component;

/** Builds Hadoop {@link FileSystem} instances for HDFS-typed environments. */
@Slf4j
@Component
public class HdfsClientFactory implements EnvironmentClientFactory<FileSystem> {

    @Override
    public EnvironmentType supportedType() {
        return EnvironmentType.HDFS;
    }

    @Override
    public FileSystem create(EnvironmentSpec spec) throws Exception {
        var conf = HadoopConfDiscovery.getHadoopConfiguration();
        var fs = FileSystem.newInstance(conf);
        log.info("HDFS FileSystem created for env spec: {}, uri: {}", spec, fs.getUri());
        return fs;
    }

    @Override
    public boolean healthy(FileSystem client) {
        try {
            client.getStatus();
            return true;
        } catch (Exception e) {
            log.warn("HDFS FileSystem health probe failed: {}", e.toString());
            return false;
        }
    }
}
