package com.flink.platform.udf;

import org.apache.flink.table.functions.ScalarFunction;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.HashMap;
import java.util.Map;

/** Timestamp format utils. */
public class TimestampFormatFunction extends ScalarFunction {

    private static final ZoneId BJ_ZONE_ID = ZoneId.of("+8");

    private static final Map<String, DateTimeFormatter> FORMATTERS = new HashMap<>();

    public String eval(Long timestamp, String format) {
        if (timestamp == null || format == null || format.length() == 0) {
            return null;
        }
        return getFormatter(format).format(Instant.ofEpochMilli(timestamp));
    }

    private static DateTimeFormatter getFormatter(String format) {
        return FORMATTERS.computeIfAbsent(
                format,
                s ->
                        new DateTimeFormatterBuilder()
                                .appendPattern(s)
                                .toFormatter()
                                .withZone(BJ_ZONE_ID));
    }

    public static void main(String[] args) {
        System.out.println(new TimestampFormatFunction().eval(1671433475595L, "yyyy-MM-dd"));
    }
}
