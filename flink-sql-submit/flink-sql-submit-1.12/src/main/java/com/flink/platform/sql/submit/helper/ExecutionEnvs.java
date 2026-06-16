package com.flink.platform.sql.submit.helper;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableEnvironment;
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
        val settingBuilder = EnvironmentSettings.newInstance().useBlinkPlanner();
        TableEnvironment tEnv;
        switch (execMode) {
            case BATCH:
                settingBuilder.inBatchMode();
                tEnv = TableEnvironment.create(settingBuilder.build());
                break;
            case STREAMING:
                settingBuilder.inStreamingMode();
                val env = StreamExecutionEnvironment.getExecutionEnvironment();
                tEnv = StreamTableEnvironment.create(env, settingBuilder.build());
                break;
            default:
                throw new FlinkJobGenException("unknown execution mode");
        }
        // Flink 1.12's EnvironmentSettings has no withConfiguration(); apply post-creation.
        tEnv.getConfig().getConfiguration().addAll(configuration);
        return new FlinkEnvironment(tEnv);
    }
}
