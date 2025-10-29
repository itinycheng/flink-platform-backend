package com.flink.platform.common.util;

/**
 * number util.
 */
public class NumberUtil {

    public static Long toLong(Object obj) {
        if (obj == null) {
            return null;
        } else if (obj instanceof Number) {
            return ((Number) obj).longValue();
        } else {
            String str = obj.toString().trim();
            return Long.parseLong(str);
        }
    }
}
