package com.itiger.persona.flink.helper;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.table.api.TableEnvironment;

/**
 * add configuration
 *
 * @author tiny.wang
 */
public class Configurations {

    public static void setConfig(TableEnvironment tEnv, String key, String value) {
        Configuration config = tEnv.getConfig().getConfiguration();
        System.out.printf("add configuration, %s=%s%n", key, value);
        config.setString(key, value);
    }

}
