package com.flink.platform.web.enums;

/** sql udf. */
public enum SqlUdf {
    TO_STRING(
            "to_string",
            "CREATE TEMPORARY SYSTEM FUNCTION to_string as 'com.flink.platform.udf.ToStringFunction' language java;"),
    LIST_CONTAINS(
            "list_contains",
            "CREATE TEMPORARY SYSTEM FUNCTION list_contains as 'com.flink.platform.udf.ListContainsValueFunction' language java;"),
    UDF_EXPRESSION("", "CREATE TEMPORARY SYSTEM FUNCTION %s as '%s' language java;");

    public final String name;

    public final String createStatement;

    SqlUdf(String name, String createStatement) {
        this.name = name;
        this.createStatement = createStatement;
    }
}
