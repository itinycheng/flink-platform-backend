package com.itiger.persona.common.util;

import org.apache.commons.lang3.time.DateUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author tiny.wang
 */
public class DateUtil {

    public static final long MILLIS_PER_MINUTE = DateUtils.MILLIS_PER_MINUTE;

    private static final Map<String, DateTimeFormatter> FORMATTERS = new ConcurrentHashMap<>();

    public static String format(Long timestamp, String format) {
        DateTimeFormatter formatter = getFormatter(format);
        return formatter.format(Instant.ofEpochMilli(timestamp));
    }

    private static DateTimeFormatter getFormatter(String format) {
        return FORMATTERS.computeIfAbsent(format, s -> new DateTimeFormatterBuilder()
                .appendPattern(s)
                .toFormatter()
                .withZone(ZoneId.of("+8")));
    }
}
