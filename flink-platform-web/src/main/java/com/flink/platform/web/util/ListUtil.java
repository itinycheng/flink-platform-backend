package com.flink.platform.web.util;

import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * List utility class.
 */
@UtilityClass
public class ListUtil {
    public static List<Object> mergeToList(Object first, Object second) {
        var list = new ArrayList<>();
        addToList(list, first);
        addToList(list, second);
        return list;
    }

    public static void addToList(List<Object> list, Object obj) {
        if (obj instanceof Collection<?> collection) {
            list.addAll(collection);
        } else {
            list.add(obj);
        }
    }
}
