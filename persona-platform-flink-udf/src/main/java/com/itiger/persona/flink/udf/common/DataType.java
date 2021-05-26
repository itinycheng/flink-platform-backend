package com.itiger.persona.flink.udf.common;

import java.util.Arrays;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

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
    BOOL;

    private static final Map<String, DataType> ENUM_MAP = Arrays.stream(values())
            .collect(toMap(Enum::name, dataType -> dataType));

    public static DataType of(String type) {
        return ENUM_MAP.get(type.toUpperCase());
    }

}
