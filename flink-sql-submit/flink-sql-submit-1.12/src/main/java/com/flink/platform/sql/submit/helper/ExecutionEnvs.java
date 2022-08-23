package com.flink.platform.sql.submit.helper;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

import com.flink.platform.common.enums.ExecutionMode;
import com.flink.platform.common.exception.FlinkJobGenException;
import lombok.val;

/** create execution environment. */
public class ExecutionEnvs {
    public static TableEnvironment createExecutionEnv(ExecutionMode execMode) {
        TableEnvironment tEnv;
        // create table Env
        val settingBuilder = EnvironmentSettings.newInstance().useBlinkPlanner();
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

        return tEnv;
    }
}
