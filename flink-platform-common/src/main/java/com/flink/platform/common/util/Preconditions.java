package com.flink.platform.common.util;

import java.util.function.Supplier;

/** check whether throw exception. */
public class Preconditions {

    public static <T> T checkNotNull(T reference) {
        if (reference == null) {
            throw new NullPointerException();
        }
        return reference;
    }

    public static <T, R> R checkNotNull(T reference, R errorMsg) {
        return reference != null ? null : errorMsg;
    }

    public static <R> R checkState(boolean expression, R errorMsg) {
        return expression ? null : errorMsg;
    }

    public static <T extends Throwable> void checkThrow(boolean condition, Supplier<T> supplier)
            throws T {
        if (condition) {
            throw supplier.get();
        }
    }
}
