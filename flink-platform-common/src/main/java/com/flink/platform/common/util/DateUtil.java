package com.flink.platform.common.util;

import org.apache.commons.lang3.time.DateUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

/** date utils. */
public class DateUtil {

    public static final String GLOBAL_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    public static final String GLOBAL_TIMEZONE = "Asia/Shanghai";

    public static final ZoneId DEFAULT_ZONE_ID =
            TimeZone.getTimeZone(GLOBAL_TIMEZONE).toZoneId();

    public static final long MILLIS_PER_MINUTE = DateUtils.MILLIS_PER_MINUTE;

    private static final Map<String, DateTimeFormatter> FORMATTERS = new ConcurrentHashMap<>();

    public static LocalDateTime toLocalDateTime(long timestamp) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), DEFAULT_ZONE_ID);
    }

    public static String format(LocalDateTime dateTime) {
        return dateTime.format(getFormatter(GLOBAL_DATE_TIME_FORMAT));
    }

    public static String format(Long timestamp, String format) {
        DateTimeFormatter formatter = getFormatter(format);
        return formatter.format(Instant.ofEpochMilli(timestamp));
    }

    public static String format(LocalDateTime dateTime, String format) {
        return dateTime.format(getFormatter(format));
    }

    private static DateTimeFormatter getFormatter(String format) {
        return FORMATTERS.computeIfAbsent(format, s -> new DateTimeFormatterBuilder()
                .appendPattern(s)
                .toFormatter()
                .withZone(DEFAULT_ZONE_ID));
    }
}
