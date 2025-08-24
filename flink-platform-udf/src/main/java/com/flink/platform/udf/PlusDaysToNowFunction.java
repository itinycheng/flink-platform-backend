package com.flink.platform.udf;

import org.apache.flink.table.functions.ScalarFunction;

import java.time.LocalDateTime;

/** Plus n days to now time. */
public class PlusDaysToNowFunction extends ScalarFunction {

    public LocalDateTime eval(int day) {
        return LocalDateTime.now().plusDays(day);
    }
}
