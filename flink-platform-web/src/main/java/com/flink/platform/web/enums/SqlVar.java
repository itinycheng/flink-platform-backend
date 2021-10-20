package com.flink.platform.web.enums;

import com.flink.platform.common.util.DateUtil;
import com.flink.platform.web.entity.JobInfo;

import java.util.function.Function;

/** sql var. */
public enum SqlVar {

    /** sql variables. */
    JOB_CODE("$jobCode", (Object obj) -> ((JobInfo) obj).getCode()),

    CURRENT_TIMESTAMP("$currentTimestamp", (Object obj) -> System.currentTimeMillis()),

    CURRENT_TIME_MINUS(
            "$currentTimeMinus",
            (Object obj) -> DateUtil.format(System.currentTimeMillis(), "yyyyMMddHHmm")),

    TODAY_YYYYMMDD(
            "$today_yyyyMMdd",
            (Object obj) -> DateUtil.format(System.currentTimeMillis(), "yyyyMMdd"));

    public final String variable;

    public final Function<Object, Object> valueProvider;

    SqlVar(String variable, Function<Object, Object> valueProvider) {
        this.variable = variable;
        this.valueProvider = valueProvider;
    }
}
