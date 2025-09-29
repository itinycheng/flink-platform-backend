package com.flink.platform.sql.submit.helper;

import org.apache.flink.table.functions.UserDefinedFunction;

import com.flink.platform.common.exception.FlinkJobGenException;
import com.flink.platform.common.job.Function;
import com.flink.platform.sql.submit.common.FlinkEnvironment;

import java.util.List;

/** create temporary system functions. */
public class Functions {

    public static void registerFunctionsToTableEnv(FlinkEnvironment env, List<Function> functions) {
        functions.forEach(function -> addFunction(env, function));
    }

    private static void addFunction(FlinkEnvironment env, Function function) {
        switch (function.getType()) {
            case TEMPORARY_SYSTEM_FUNCTION:
                Class<? extends UserDefinedFunction> tmpSysFunc = loadClass(function);
                env.createTemporarySystemFunction(function.getName(), tmpSysFunc);

                break;
            case TEMPORARY_FUNCTION:
                Class<? extends UserDefinedFunction> tmpFunc = loadClass(function);
                env.createTemporaryFunction(function.getName(), tmpFunc);
                break;
            default:
                throw new FlinkJobGenException("Unsupported function type, function: " + function);
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends UserDefinedFunction> loadClass(Function function) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            return (Class<? extends UserDefinedFunction>) Class.forName(function.getClazz(), true, classLoader);
        } catch (Exception e) {
            throw new FlinkJobGenException(String.format("cannot add temporary system function: %s", function), e);
        }
    }
}
