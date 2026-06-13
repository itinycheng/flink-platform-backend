package com.flink.platform.sql.submit;

import com.flink.platform.common.enums.ExecutionMode;
import com.flink.platform.sql.submit.base.SqlApplication;
import com.flink.platform.sql.submit.base.common.FlinkEnvAdapter;
import com.flink.platform.sql.submit.helper.ExecutionEnvs;

import java.util.Map;

/** Used to execute sql of flink 1.15, must be a valid sql context file. */
public class Sql115Application extends SqlApplication {

    public static void main(String[] args) throws Exception {
        new Sql115Application().run(args);
    }

    @Override
    protected FlinkEnvAdapter createExecutionEnv(ExecutionMode execMode, Map<String, String> configMap) {
        return ExecutionEnvs.createExecutionEnv(execMode, configMap);
    }
}
