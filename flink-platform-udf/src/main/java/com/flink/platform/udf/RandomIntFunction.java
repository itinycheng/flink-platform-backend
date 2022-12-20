package com.flink.platform.udf;

import org.apache.flink.table.functions.ScalarFunction;

import java.util.concurrent.ThreadLocalRandom;

/** Random integer. */
public class RandomIntFunction extends ScalarFunction {
    public int eval() {
        return ThreadLocalRandom.current().nextInt();
    }

    @Override
    public boolean isDeterministic() {
        return false;
    }
}
