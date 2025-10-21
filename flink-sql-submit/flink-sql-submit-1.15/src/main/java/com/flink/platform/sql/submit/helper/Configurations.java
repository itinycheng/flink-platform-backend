package com.flink.platform.sql.submit.helper;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

import com.flink.platform.sql.submit.common.FlinkEnvironment;

/** add configuration. */
public class Configurations {

    public static void setConfig(FlinkEnvironment env, String key, String value) {
        StreamTableEnvironment tEnv = env.getTableEnv();
        Configuration config = tEnv.getConfig().getConfiguration();
        System.out.printf("add configuration, %s=%s%n", key, value);
        config.setString(key, value);
    }
}
