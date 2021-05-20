package com.itiger.persona.enums;

import com.itiger.persona.common.util.DateUtil;
import com.itiger.persona.entity.JobInfo;

import java.util.function.Function;

/**
 * @author tiny.wang
 */
public enum SqlVar {

    /**
     * sql variable
     */
    JOB_CODE("$jobCode", (Object obj) -> ((JobInfo) obj).getCode()),

    CURRENT_TIMESTAMP("$currentTimestamp", (Object obj) -> System.currentTimeMillis()),

    CURRENT_TIME_MINUS("$currentTimeMinus", (Object obj) ->
            DateUtil.format(System.currentTimeMillis(), "yyyyMMddHHmm"));

    public final String variable;

    public final Function<Object, Object> valueProvider;

    SqlVar(String variable, Function<Object, Object> valueProvider) {
        this.variable = variable;
        this.valueProvider = valueProvider;
    }
}
