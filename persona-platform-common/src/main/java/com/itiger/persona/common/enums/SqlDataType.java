package com.itiger.persona.common.enums;

import java.util.Arrays;
import java.util.Map;

import static com.itiger.persona.common.constants.Constant.EMPTY;
import static com.itiger.persona.common.constants.Constant.SINGLE_QUOTE;
import static java.util.stream.Collectors.toMap;

/**
 * @author tiger
 */

public enum SqlDataType {
    /**
     * data type
     */
    @Deprecated
    NUMBER(EMPTY, "DOUBLE"),

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

    SqlDataType(String quote, String sqlType) {
        this.quote = quote;
        this.sqlType = sqlType;
    }

    private static final Map<String, SqlDataType> ENUM_MAP = Arrays.stream(values())
            .collect(toMap(Enum::name, dataType -> dataType));

    public static SqlDataType of(String type) {
        return ENUM_MAP.get(type.toUpperCase());
    }
}
