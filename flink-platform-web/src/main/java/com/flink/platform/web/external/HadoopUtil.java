package com.flink.platform.web.external;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.conf.YarnConfiguration;

import java.io.File;

/** Hadoop util. */
@Slf4j
public class HadoopUtil {

    public static Configuration getHadoopConfiguration() {
        Configuration result = new YarnConfiguration();
        boolean foundHadoopConfiguration = false;

        // Approach 1: HADOOP_HOME environment variables
        String[] possibleHadoopConfPaths = new String[2];

        final String hadoopHome = System.getenv("HADOOP_HOME");
        if (hadoopHome != null) {
            log.debug("Searching Hadoop configuration files in HADOOP_HOME: {}", hadoopHome);
            possibleHadoopConfPaths[0] = hadoopHome + "/conf";
            possibleHadoopConfPaths[1] = hadoopHome + "/etc/hadoop"; // hadoop 2.2
        }

        for (String possibleHadoopConfPath : possibleHadoopConfPaths) {
            if (possibleHadoopConfPath != null) {
                foundHadoopConfiguration = addHadoopConfIfFound(result, possibleHadoopConfPath);
            }
        }

        // Approach 2: HADOOP_CONF_DIR environment variable
        String hadoopConfDir = System.getenv("HADOOP_CONF_DIR");
        if (hadoopConfDir != null) {
            log.debug("Searching Hadoop configuration files in HADOOP_CONF_DIR: {}", hadoopConfDir);
            foundHadoopConfiguration =
                    addHadoopConfIfFound(result, hadoopConfDir) || foundHadoopConfiguration;
        }

        if (!foundHadoopConfiguration) {
            throw new RuntimeException(
                    "Could not find Hadoop configuration via any of the supported methods "
                            + "(Flink configuration, environment variables).");
        }

        return result;
    }

    private static boolean addHadoopConfIfFound(
            Configuration configuration, String possibleHadoopConfPath) {
        boolean foundHadoopConfiguration = false;
        if (new File(possibleHadoopConfPath).exists()) {
            String coreSitePath = possibleHadoopConfPath + "/core-site.xml";
            if (new File(coreSitePath).exists()) {
                configuration.addResource(new Path(coreSitePath));
                log.debug(
                        "Adding {}/core-site.xml to hadoop configuration", possibleHadoopConfPath);
                foundHadoopConfiguration = true;
            }

            String hdfsSitePath = possibleHadoopConfPath + "/hdfs-site.xml";
            if (new File(hdfsSitePath).exists()) {
                configuration.addResource(new Path(hdfsSitePath));
                log.debug(
                        "Adding {}/hdfs-site.xml to hadoop configuration", possibleHadoopConfPath);
                foundHadoopConfiguration = true;
            }

            String yarnSitePath = possibleHadoopConfPath + "/yarn-site.xml";
            if (new File(yarnSitePath).exists()) {
                configuration.addResource(new Path(yarnSitePath));
                log.debug(
                        "Adding {}/yarn-site.xml to hadoop configuration", possibleHadoopConfPath);
                foundHadoopConfiguration = true;
            }
        }

        return foundHadoopConfiguration;
    }
}
