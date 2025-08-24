package com.flink.platform.udf;

import org.apache.flink.table.functions.ScalarFunction;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDateTime;

import static com.flink.platform.common.constants.Constant.GLOBAL_ZONE_ID;
import static com.flink.platform.common.util.DateUtil.getFormatter;

/** Date time utils. */
@Slf4j
public class ToLocalDateTimeFunction extends ScalarFunction {

    public LocalDateTime eval(Long timestamp) {
        if (timestamp == null) {
            return null;
        }

        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), GLOBAL_ZONE_ID);
    }

    public LocalDateTime eval(String timeStr, String format) {
        try {
            if (timeStr == null || format == null) {
                return null;
            }

            if (timeStr.length() < format.length()) {
                format = format.substring(0, timeStr.length());
            }

            return LocalDateTime.parse(timeStr, getFormatter(format));
        } catch (Exception e) {
            log.error("parse time literal failed", e);
            return null;
        }
    }

    public static void main(String[] args) {
        LocalDateTime eval = new ToLocalDateTimeFunction().eval("2022-12-05 12:20:30", "yyyy-MM-dd HH:mm:ss.SSS000000");
        System.out.println(eval);
    }
}
