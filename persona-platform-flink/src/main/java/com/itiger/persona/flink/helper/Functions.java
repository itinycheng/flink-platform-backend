package com.itiger.persona.flink.helper;

import com.itiger.persona.common.job.Function;
import com.itiger.persona.common.job.FlinkJobGenException;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.functions.UserDefinedFunction;

import java.util.List;

/**
 * create temporary system functions
 *
 * @author tiny.wang
 */
public class Functions {

    public static void registerFunctionsToTableEnv(TableEnvironment tEnv, List<Function> functions) {
        functions.forEach(function -> addFunction(tEnv, function));
    }

    private static void addFunction(TableEnvironment tEnv, Function function) {
        switch (function.getType()) {
            case TEMPORARY_SYSTEM_FUNCTION:
                Class<? extends UserDefinedFunction> tmpSysFunc = loadClass(function);
                tEnv.createTemporarySystemFunction(function.getName(), tmpSysFunc);

                break;
            case TEMPORARY_FUNCTION:
                Class<? extends UserDefinedFunction> tmpFunc = loadClass(function);
                tEnv.createTemporaryFunction(function.getName(), tmpFunc);
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
