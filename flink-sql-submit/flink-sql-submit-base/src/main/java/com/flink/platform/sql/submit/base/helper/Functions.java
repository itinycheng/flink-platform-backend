package com.flink.platform.sql.submit.base.helper;

import com.flink.platform.common.exception.FlinkJobGenException;
import com.flink.platform.common.job.Function;
import com.flink.platform.sql.submit.base.common.FlinkEnvAdapter;

import java.util.List;

/** create temporary system functions. */
public class Functions {

    public static void registerFunctionsToTableEnv(FlinkEnvAdapter env, List<Function> functions) {
        functions.forEach(function -> addFunction(env, function));
    }

    private static void addFunction(FlinkEnvAdapter env, Function function) {
        switch (function.getType()) {
            case TEMPORARY_SYSTEM_FUNCTION:
                env.createTemporarySystemFunction(function.getName(), function.getClazz());
                break;
            case TEMPORARY_FUNCTION:
                env.createTemporaryFunction(function.getName(), function.getClazz());
                break;
            default:
                throw new FlinkJobGenException("Unsupported function type, function: " + function);
        }
    }
}
