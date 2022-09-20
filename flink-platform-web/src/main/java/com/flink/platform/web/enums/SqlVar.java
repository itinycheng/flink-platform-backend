package com.flink.platform.web.enums;

import com.flink.platform.common.util.DateUtil;
import com.flink.platform.dao.entity.JobRunInfo;
import org.codehaus.commons.compiler.CompilerFactoryFactory;
import org.codehaus.commons.compiler.IScriptEvaluator;

import java.util.Arrays;
import java.util.function.Function;

import static com.flink.platform.common.util.FunctionUtil.uncheckedFunction;

/** sql var. */
public enum SqlVar {

    /** sql variables. */
    JOB_CODE("${jobCode}", VarType.VARIABLE, (Object obj) -> ((JobRunInfo) obj).getJobCode()),

    CURRENT_TIMESTAMP(
            "${currentTimestamp}", VarType.VARIABLE, (Object obj) -> System.currentTimeMillis()),

    CURRENT_TIME_MINUS(
            "${currentTimeMinus}",
            VarType.VARIABLE,
            (Object obj) -> DateUtil.format(System.currentTimeMillis(), "yyyyMMddHHmm")),

    TODAY_YYYYMMDD(
            "${today_yyyyMMdd}",
            VarType.VARIABLE,
            (Object obj) -> DateUtil.format(System.currentTimeMillis(), "yyyyMMdd")),

    LITERAL("${literal", VarType.LITERAL, Object::toString),

    // TODO optimize code in the future.
    SCRIPT(
            "${script",
            VarType.SCRIPT,
            uncheckedFunction(
                    (methodBody) -> {
                        ClassLoader contextClassLoader =
                                Thread.currentThread().getContextClassLoader();
                        IScriptEvaluator evaluator =
                                CompilerFactoryFactory.getDefaultCompilerFactory(contextClassLoader)
                                        .newScriptEvaluator();
                        evaluator.setReturnType(String.class);
                        evaluator.cook((String) methodBody);
                        Object evaluate = evaluator.evaluate(null);
                        return String.valueOf(evaluate);
                    }));

    public final String variable;

    public final VarType type;

    public final Function<Object, Object> valueProvider;

    SqlVar(String variable, VarType type, Function<Object, Object> valueProvider) {
        this.variable = variable;
        this.type = type;
        this.valueProvider = valueProvider;
    }

    public static SqlVar matchPrefix(String name) {
        return Arrays.stream(values())
                .filter(sqlVar -> name.startsWith(sqlVar.variable))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Unrecognized type, variable: " + name));
    }

    /** variable type. */
    public enum VarType {
        /** variable type. */
        LITERAL,
        SCRIPT,
        VARIABLE
    }
}
