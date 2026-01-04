package com.flink.platform.web.util;

import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Object utility class.
 */
@UtilityClass
public class ObjectUtil {

    public static <K> Map<K, Object> merge(Map<K, Object> first, Map<K, Object> second) {
        if (first == null && second == null) {
            return Map.of();
        }

        if (first == null || second == null) {
            return first != null ? first : second;
        }

        return Stream.of(first, second)
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, ObjectUtil::mergeToList));
    }

    public static List<Object> mergeToList(Object first, Object second) {
        var list = new ArrayList<>();
        addToList(list, first);
        addToList(list, second);
        return list;
    }

    public static void addToList(List<Object> list, Object obj) {
        if (obj instanceof Collection<?> collection) {
            list.addAll(collection);
        } else if (obj != null) {
            list.add(obj);
        }
    }
}
