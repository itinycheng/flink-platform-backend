package com.flink.platform.common.util;

import static com.flink.platform.common.constants.Constant.ELLIPSIS;
import static com.flink.platform.common.constants.Constant.EMPTY;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * string util.
 */
public class StringUtil {

    public static int byteLength(String str) {
        return str == null ? 0 : str.getBytes(UTF_8).length;
    }

    public static String truncateByBytes(String str, int maxBytes) {
        if (str == null || str.isEmpty() || maxBytes <= 0) {
            return str;
        }

        byte[] bytes = str.getBytes(UTF_8);
        if (bytes.length <= maxBytes) {
            return str;
        }

        int pos = maxBytes;
        while (pos > 0 && (bytes[pos] & 0x80) != 0 && (bytes[pos] & 0xC0) != 0xC0) {
            pos--;
        }

        if (pos == 0 && (bytes[0] & 0x80) != 0) {
            return EMPTY;
        }

        return new String(bytes, 0, pos, UTF_8);
    }

    public static String truncateByBytes(String str, int maxBytes, boolean ellipsis) {
        if (!ellipsis) {
            return truncateByBytes(str, maxBytes);
        }

        int ellipsisBytes = ELLIPSIS.getBytes(UTF_8).length;
        if (maxBytes <= ellipsisBytes) {
            return truncateByBytes(str, maxBytes);
        }

        String truncated = truncateByBytes(str, maxBytes - ellipsisBytes);
        return truncated.length() < str.length() ? truncated + ELLIPSIS : str;
    }
}
