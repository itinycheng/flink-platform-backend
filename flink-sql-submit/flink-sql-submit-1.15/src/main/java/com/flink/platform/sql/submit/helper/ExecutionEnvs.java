package com.flink.platform.sql.submit.helper;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

import com.flink.platform.common.enums.ExecutionMode;
import com.flink.platform.common.exception.FlinkJobGenException;
import com.flink.platform.sql.submit.common.FlinkEnvironment;
import lombok.val;

/** create execution environment. */
public class ExecutionEnvs {
    public static FlinkEnvironment createExecutionEnv(ExecutionMode execMode, Configuration configuration) {
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

        val env = StreamExecutionEnvironment.getExecutionEnvironment();
        val streamEnv = StreamTableEnvironment.create(env, settingBuilder.build());
        return new FlinkEnvironment(env, streamEnv);
    }
}
