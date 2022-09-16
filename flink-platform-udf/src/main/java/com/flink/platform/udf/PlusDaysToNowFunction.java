package com.flink.platform.udf;

import org.apache.flink.table.functions.ScalarFunction;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Plus n days to now time. */
public class PlusDaysToNowFunction extends ScalarFunction {

    public LocalDateTime eval(int day) {
        return LocalDateTime.now().plus(day, ChronoUnit.DAYS);
    }

    @Override
    public boolean isDeterministic() {
        return true;
    }
}
