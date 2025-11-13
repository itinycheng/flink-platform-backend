package com.flink.platform.web.enums;

import org.codehaus.commons.compiler.CompilerFactoryFactory;

import java.util.Arrays;
import java.util.function.Function;

import static com.flink.platform.common.util.FunctionUtil.uncheckedFunction;

/** sql var. */
public enum Variable {

    /** variables. */
    // TODO optimize code in the future.
    SCRIPT("${script", uncheckedFunction((methodBody) -> {
        var contextClassLoader = Thread.currentThread().getContextClassLoader();
        var evaluator = CompilerFactoryFactory.getDefaultCompilerFactory(contextClassLoader)
                .newScriptEvaluator();
        evaluator.setReturnType(String.class);
        evaluator.cook((String) methodBody);
        var evaluate = evaluator.evaluate(null);
        return String.valueOf(evaluate);
    }));

    public final String wildcard;

    public final Function<Object, Object> provider;

    Variable(String wildcard, Function<Object, Object> provider) {
        this.wildcard = wildcard;
        this.provider = provider;
    }

    public Object apply(Object object) {
        return provider.apply(object);
    }

    public static Variable matchPrefix(String name) {
        return Arrays.stream(values())
                .filter(sqlVar -> name.startsWith(sqlVar.wildcard))
                .findFirst()
                .orElse(null);
    }
}
