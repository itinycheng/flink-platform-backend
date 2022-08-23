package com.flink.platform.sql.submit.helper;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.table.api.TableEnvironment;

/** add configuration. */
public class Configurations {

    public static void setConfig(TableEnvironment tEnv, String key, String value) {
        Configuration config = tEnv.getConfig().getConfiguration();
        System.out.printf("add configuration, %s=%s%n", key, value);
        config.setString(key, value);
    }
}
