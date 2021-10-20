package com.flink.platform.udf.util;

import com.flink.platform.udf.common.SqlColumn;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static java.util.stream.Collectors.toList;

/** Class utils. */
public class ClassUtil {

    public static List<SqlColumn> extractSqlColumnAnnotation(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .map(field -> field.getAnnotation(SqlColumn.class))
                .sorted(Comparator.comparing(SqlColumn::priority))
                .collect(toList());
    }
}
