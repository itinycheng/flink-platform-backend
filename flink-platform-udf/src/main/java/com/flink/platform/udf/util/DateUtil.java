package com.flink.platform.udf.util;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

/** Date utils for udf. */
public class DateUtil {

    public static final String UDF_TIMEZONE = "Asia/Shanghai";

    public static final ZoneId UDF_ZONE_ID = TimeZone.getTimeZone(UDF_TIMEZONE).toZoneId();

    private static final Map<String, DateTimeFormatter> FORMATTERS = new ConcurrentHashMap<>();

    public static DateTimeFormatter getFormatter(String format) {
        return FORMATTERS.computeIfAbsent(format, s -> new DateTimeFormatterBuilder()
                .appendPattern(s)
                .toFormatter()
                .withZone(UDF_ZONE_ID));
    }
}
