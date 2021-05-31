package com.itiger.persona.flink.udf.util;

import com.itiger.persona.flink.udf.common.SqlColumn;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * @author tiny.wang
 */
public class UdfUtil {

    public static List<SqlColumn> extractSqlColumnAnnotation(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .map(field -> field.getAnnotation(SqlColumn.class))
                .sorted(Comparator.comparing(SqlColumn::priority))
                .collect(toList());
    }
}
