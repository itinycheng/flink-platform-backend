package com.flink.platform.web.util;

import java.util.Collection;

/**
 * collection utils.
 */
public class CollectionUtil {
    public static <T> boolean contains(Collection<T> base, T element) {
        if (base == null || element == null) {
            return false;
        }

        return base.contains(element);
    }
}
