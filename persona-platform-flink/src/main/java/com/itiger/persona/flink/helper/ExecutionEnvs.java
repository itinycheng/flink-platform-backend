package com.itiger.persona.flink.helper;

import com.itiger.persona.common.enums.ExecutionMode;
import com.itiger.persona.common.exception.FlinkJobGenException;
import lombok.val;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

/**
 * create execution environment
 *
 * @author tiny.wang
 */
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
