package com.flink.platform.environment.hadoop;

import com.flink.platform.common.environment.EnvironmentSpec;
import com.flink.platform.common.environment.EnvironmentType;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.conf.HAUtil;

import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.firstNonBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.hadoop.fs.CommonConfigurationKeysPublic.FS_DEFAULT_NAME_KEY;
import static org.apache.hadoop.hdfs.client.HdfsClientConfigKeys.DFS_NAMESERVICES;
import static org.apache.hadoop.yarn.conf.YarnConfiguration.RM_ADDRESS;
import static org.apache.hadoop.yarn.conf.YarnConfiguration.RM_CLUSTER_ID;
import static org.apache.hadoop.yarn.conf.YarnConfiguration.RM_HOSTNAME;

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

        var hdfsName = firstNonBlank(conf.get(FS_DEFAULT_NAME_KEY), conf.get(DFS_NAMESERVICES));
        if (isNotBlank(hdfsName)) {
            out.add(new EnvironmentSpec(EnvironmentType.HDFS, hdfsName));
            log.info("Detected HDFS env: name = {}", hdfsName);
        }

        var yarnName = firstNonBlank(conf.get(RM_CLUSTER_ID), conf.get(RM_ADDRESS), conf.get(RM_HOSTNAME));
        if (HAUtil.isHAEnabled(conf) || isNotBlank(yarnName)) {
            yarnName = defaultString(yarnName, "<yarn>");
            out.add(new EnvironmentSpec(EnvironmentType.YARN, yarnName));
            log.info("Detected YARN env: name = {}", yarnName);
        }

        return out;
    }
}
