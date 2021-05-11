package com.itiger.persona.enums;

/**
 * @author tiny.wang
 */
public enum SqlVar {

    /**
     * sql variable
     */
    JOB_CODE("$jobCode"),

    CURRENT_TIMESTAMP("$currentTimestamp");

    public final String variable;

    SqlVar(String variable) {
        this.variable = variable;
    }
}
