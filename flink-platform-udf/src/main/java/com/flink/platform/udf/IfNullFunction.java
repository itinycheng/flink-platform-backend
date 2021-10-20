package com.flink.platform.udf;

import org.apache.flink.table.functions.ScalarFunction;

/** return a default value if `src` is null. */
public class IfNullFunction extends ScalarFunction {

    public Integer eval(Integer src, Integer replace) {
        return src != null ? src : replace;
    }
}
