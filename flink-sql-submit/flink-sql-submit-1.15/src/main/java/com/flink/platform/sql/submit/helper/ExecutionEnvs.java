package com.flink.platform.sql.submit.helper;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableEnvironment;

import com.flink.platform.common.enums.ExecutionMode;
import com.flink.platform.common.exception.FlinkJobGenException;
import lombok.val;

/** create execution environment. */
public class ExecutionEnvs {
    public static TableEnvironment createExecutionEnv(ExecutionMode execMode, Configuration configuration) {
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
        return TableEnvironment.create(settingBuilder.build());
    }
}
