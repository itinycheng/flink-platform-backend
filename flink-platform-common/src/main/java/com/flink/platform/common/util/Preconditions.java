package com.flink.platform.common.util;

import java.util.function.Supplier;

/** check whether throw exception. */
public class Preconditions {

    public static <T extends Throwable> void checkThrow(boolean condition, Supplier<T> supplier)
            throws T {
        if (condition) {
            throw supplier.get();
        }
    }
}
