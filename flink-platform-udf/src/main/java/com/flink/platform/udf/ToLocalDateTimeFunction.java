package com.flink.platform.udf;

import org.apache.flink.table.functions.ScalarFunction;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * @author tiny.wang
 */
public class ToLocalDateTimeFunction extends ScalarFunction {

    private static final ZoneId BJ_ZONE_ID = ZoneId.of("+8");

    public LocalDateTime eval(Long timestamp) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), BJ_ZONE_ID);
    }

}
