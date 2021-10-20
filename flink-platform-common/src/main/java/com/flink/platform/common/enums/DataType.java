package com.flink.platform.common.enums;

import java.util.Arrays;
import java.util.Map;

import static com.flink.platform.common.constants.Constant.EMPTY;
import static com.flink.platform.common.constants.Constant.SINGLE_QUOTE;
import static java.util.stream.Collectors.toMap;

/** sql data type. */
public enum DataType {
    @Deprecated
    NUMBER(EMPTY, "UNDEFINED"),

    STRING(SINGLE_QUOTE, "STRING"),

    LIST(EMPTY, "ARRAY"),

    LIST_MAP(EMPTY, "ARRAY<MAP<STRING,STRING>>"),

    MAP(EMPTY, "MAP"),

    INT(EMPTY, "INTEGER"),

    LONG(EMPTY, "BIGINT"),

    FLOAT(EMPTY, "FLOAT"),

    DOUBLE(EMPTY, "DOUBLE"),

    BOOL(EMPTY, "BOOLEAN");

    public final String quote;

    public final String sqlType;

    DataType(String quote, String sqlType) {
        this.quote = quote;
        this.sqlType = sqlType;
    }

    private static final Map<String, DataType> ENUM_MAP =
            Arrays.stream(values()).collect(toMap(Enum::name, dataType -> dataType));

    public static DataType of(String type) {
        return ENUM_MAP.get(type.toUpperCase());
    }
}
