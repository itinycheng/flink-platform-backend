package com.itiger.persona.enums;

/**
 * @author tiny.wang
 */
public enum SqlUdf {

    /**
     * to string
     */
    TO_STRING("to_string", "CREATE TEMPORARY SYSTEM FUNCTION to_string as 'com.itiger.persona.flink.udf.ToStringFunction' language java;"),
    LIST_CONTAINS("list_contains", "CREATE TEMPORARY SYSTEM FUNCTION list_contains as 'com.itiger.persona.flink.udf.ListContainsValueFunction' language java;");

    public final String name;

    public final String createStatement;

    SqlUdf(String name, String createStatement) {
        this.name = name;
        this.createStatement = createStatement;
    }

}
