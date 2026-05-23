package com.flink.platform.environment.hadoop;

import com.flink.platform.common.environment.EnvironmentSpec;
import com.flink.platform.common.environment.EnvironmentType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Probes the local node for a Hadoop configuration via {@link HadoopConfDiscovery} (HADOOP_HOME /
 * HADOOP_CONF_DIR) and emits at most one HDFS spec and one YARN spec.
 */
@Slf4j
public final class HadoopDetector {

    private HadoopDetector() {}

    public static List<EnvironmentSpec> detect() {
        return HadoopConfDiscovery.tryGetHadoopConfiguration()
                .map(HadoopDetector::specsFromConf)
                .orElseGet(List::of);
    }

    private static List<EnvironmentSpec> specsFromConf(Configuration conf) {
        var out = new ArrayList<EnvironmentSpec>();

        var nameServices = conf.get("dfs.nameservices");
        String hdfsName = null;
        if (StringUtils.isNotBlank(nameServices)) {
            hdfsName = nameServices.split(",")[0].trim();
            out.add(new EnvironmentSpec(EnvironmentType.HDFS, hdfsName));
            log.info("Detected HDFS env: name = {}", hdfsName);
        }

        var rmAddress = conf.get("yarn.resourcemanager.address");
        var rmHostname = conf.get("yarn.resourcemanager.hostname");
        if (StringUtils.isNotBlank(rmAddress) || StringUtils.isNotBlank(rmHostname)) {
            var yarnId = conf.get("yarn.cluster.id", hdfsName);
            if (StringUtils.isNotBlank(yarnId)) {
                out.add(new EnvironmentSpec(EnvironmentType.YARN, yarnId));
                log.info("Detected YARN env: name = {}", yarnId);
            }
        }

        return out;
    }
}
