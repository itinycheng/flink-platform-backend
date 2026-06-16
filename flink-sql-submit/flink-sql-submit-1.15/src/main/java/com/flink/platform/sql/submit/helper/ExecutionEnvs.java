package com.flink.platform.sql.submit.helper;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

import com.flink.platform.common.enums.ExecutionMode;
import com.flink.platform.common.exception.FlinkJobGenException;
import com.flink.platform.sql.submit.base.common.FlinkEnvAdapter;
import com.flink.platform.sql.submit.common.FlinkEnvironment;
import lombok.val;

import java.util.Map;

/** create execution environment. */
public class ExecutionEnvs {

    public static FlinkEnvAdapter createExecutionEnv(ExecutionMode execMode, Map<String, String> configMap) {
        val configuration = Configuration.fromMap(configMap);
        val settingBuilder = EnvironmentSettings.newInstance().withConfiguration(configuration);
        switch (execMode) {
            case BATCH:
                settingBuilder.inBatchMode();
                break;
            case STREAMING:
                settingBuilder.inStreamingMode();
                break;
            default:
                throw new FlinkJobGenException("unknown execution mode");
        }

        val env = StreamExecutionEnvironment.getExecutionEnvironment(configuration);
        val streamEnv = StreamTableEnvironment.create(env, settingBuilder.build());
        return new FlinkEnvironment(env, streamEnv);
    }
}
