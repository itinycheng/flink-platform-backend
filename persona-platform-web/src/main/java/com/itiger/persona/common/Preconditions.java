package com.itiger.persona.common;

import java.util.function.Supplier;

/**
 * @author tiny
 */
public class Preconditions {

    public static <T extends Throwable> void checkThrow(boolean condition, Supplier<T> supplier) throws T {
        if (condition) {
            throw supplier.get();
        }
    }
}
