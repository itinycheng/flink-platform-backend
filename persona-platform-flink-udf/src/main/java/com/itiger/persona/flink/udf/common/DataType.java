package com.itiger.persona.flink.udf.common;

import java.util.Arrays;

/**
 * @author tiny.wang
 */
public enum DataType {
    /**
     * enums
     */
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    STRING,
    BOOL,
    OTHER;

    public static DataType of(String type) {
        return Arrays.stream(values())
                .filter(dataType -> dataType.name().equalsIgnoreCase(type))
                .findFirst()
                .orElse(OTHER);
    }

}
