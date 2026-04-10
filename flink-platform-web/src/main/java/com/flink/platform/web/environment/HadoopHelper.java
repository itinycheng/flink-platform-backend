package com.flink.platform.web.environment;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;

import java.io.File;
import java.util.ArrayList;

/** Hadoop util. */
@Slf4j
public class HadoopHelper {

    public static Configuration getHadoopConfiguration() {
        var result = new Configuration();
        var foundHadoopConfiguration = false;

        // Approach 1: HADOOP_HOME environment variables
        var possibleHadoopConfPaths = new ArrayList<String>(2);

        final var hadoopHome = System.getenv("HADOOP_HOME");
        if (hadoopHome != null) {
            log.info("Searching Hadoop configuration files in HADOOP_HOME: {}", hadoopHome);
            possibleHadoopConfPaths.add(hadoopHome + "/conf");
            possibleHadoopConfPaths.add(hadoopHome + "/etc/hadoop"); // hadoop 2.2
        }

        for (var possibleHadoopConfPath : possibleHadoopConfPaths) {
            foundHadoopConfiguration = addHadoopConfIfFound(result, possibleHadoopConfPath);
        }

        // Approach 2: HADOOP_CONF_DIR environment variable
        var hadoopConfDir = System.getenv("HADOOP_CONF_DIR");
        if (hadoopConfDir != null) {
            log.info("Searching Hadoop configuration files in HADOOP_CONF_DIR: {}", hadoopConfDir);
            foundHadoopConfiguration = addHadoopConfIfFound(result, hadoopConfDir) || foundHadoopConfiguration;
        }

        if (!foundHadoopConfiguration) {
            throw new RuntimeException("Could not find Hadoop configuration via any of the supported methods "
                    + "(Hadoop configuration, environment variables).");
        }

        return result;
    }

    private static boolean addHadoopConfIfFound(Configuration configuration, String possibleHadoopConfPath) {
        var foundHadoopConfiguration = false;
        if (new File(possibleHadoopConfPath).exists()) {
            var coreSitePath = possibleHadoopConfPath + "/core-site.xml";
            if (new File(coreSitePath).exists()) {
                configuration.addResource(new Path(coreSitePath));
                log.info("Adding {}/core-site.xml to hadoop configuration", possibleHadoopConfPath);
                foundHadoopConfiguration = true;
            }

            var hdfsSitePath = possibleHadoopConfPath + "/hdfs-site.xml";
            if (new File(hdfsSitePath).exists()) {
                configuration.addResource(new Path(hdfsSitePath));
                log.info("Adding {}/hdfs-site.xml to hadoop configuration", possibleHadoopConfPath);
                foundHadoopConfiguration = true;
            }

            var yarnSitePath = possibleHadoopConfPath + "/yarn-site.xml";
            if (new File(yarnSitePath).exists()) {
                configuration.addResource(new Path(yarnSitePath));
                log.info("Adding {}/yarn-site.xml to hadoop configuration", possibleHadoopConfPath);
                foundHadoopConfiguration = true;
            }
        }

        return foundHadoopConfiguration;
    }
}
