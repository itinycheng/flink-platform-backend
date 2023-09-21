package com.flink.platform.udf;

import org.apache.flink.table.functions.ScalarFunction;

import java.time.Instant;
import java.time.LocalDateTime;

import static com.flink.platform.udf.util.DateUtil.getFormatter;

/** Timestamp format utils. */
public class TimeFormatFunction extends ScalarFunction {

    public String eval(Long timestamp, String format) {
        if (timestamp == null || format == null || format.isEmpty()) {
            return null;
        }
        return getFormatter(format).format(Instant.ofEpochMilli(timestamp));
    }

    public String eval(LocalDateTime time, String format) {
        if (time == null || format == null || format.isEmpty()) {
            return null;
        }
        return getFormatter(format).format(time);
    }

    public static void main(String[] args) {
        System.out.println(new TimeFormatFunction().eval(1671700767810L, "yyyy-MM-dd HH:mm:ss"));

        System.out.println(new TimeFormatFunction().eval(LocalDateTime.now(), "yyyy-MM-dd HH:mm:ss"));
    }
}
