package com.flink.platform.sql.submit.base.helper;

import com.flink.platform.sql.submit.base.common.FlinkEnvAdapter;

/** Apply runtime configuration to a Flink table environment via {@link FlinkEnvAdapter}. */
public class Configurations {

    public static void setConfig(FlinkEnvAdapter env, String key, String value) {
        System.out.printf("add configuration, %s=%s%n", key, value);
        env.setConfig(key, value);
    }
}
