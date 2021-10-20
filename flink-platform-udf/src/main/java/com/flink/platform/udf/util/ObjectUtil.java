package com.flink.platform.udf.util;

import java.util.Arrays;

/** Object utils. */
public class ObjectUtil {

    public static Object[] cast(String[] arr, Class<?> clazz) {
        if (clazz.isAssignableFrom(String.class)) {
            return arr;
        } else if (clazz.isAssignableFrom(Long.class)) {
            return Arrays.stream(arr).map(Long::parseLong).toArray();
        } else if ((clazz.isAssignableFrom(Double.class))) {
            return Arrays.stream(arr).map(Double::parseDouble).toArray();
        } else if (clazz.isAssignableFrom(Integer.class)) {
            return Arrays.stream(arr).map(Integer::parseInt).toArray();
        } else if (clazz.isAssignableFrom(Float.class)) {
            return Arrays.stream(arr).map(Float::parseFloat).toArray();
        } else {
            throw new RuntimeException(String.format("Unsupported data type %s", clazz.toString()));
        }
    }
}
