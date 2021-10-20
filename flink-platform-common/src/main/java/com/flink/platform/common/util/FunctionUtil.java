package com.flink.platform.common.util;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** function utils. */
public class FunctionUtil {

    public static <A> Consumer<A> uncheckedConsumer(ConsumerWithException<A, ?> func) {
        return (A value) -> {
            try {
                func.accept(value);
            } catch (Throwable t) {
                rethrow(t);
            }
        };
    }

    public static <T> Supplier<T> uncheckedSupplier(SupplierWithException<T, ?> func) {
        return () -> {
            try {
                return func.get();
            } catch (Throwable e) {
                rethrow(e);
                return null;
            }
        };
    }

    public static <T> T getOrDefault(SupplierWithException<T, ?> func, T t) {
        try {
            return func.get();
        } catch (Throwable e) {
            return t;
        }
    }

    public static <T, R> Function<T, R> uncheckedFunction(FunctionWithException<T, R, ?> func) {
        return (T t) -> {
            try {
                return func.apply(t);
            } catch (Throwable e) {
                rethrow(e);
                return null;
            }
        };
    }

    public static void rethrow(Throwable t) {
        if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        } else {
            throw new RuntimeException(t);
        }
    }

    /**
     * A functional interface for a {@link java.util.function.Function} that may throw exceptions.
     */
    @FunctionalInterface
    public interface FunctionWithException<T, R, E extends Throwable> {

        R apply(T value) throws E;
    }

    /**
     * A functional interface for a {@link java.util.function.Supplier} that may throw exceptions.
     */
    @FunctionalInterface
    public interface SupplierWithException<R, E extends Throwable> {

        R get() throws E;
    }

    /**
     * A functional interface for a {@link java.util.function.Consumer} that may throw exceptions.
     */
    @FunctionalInterface
    public interface ConsumerWithException<T, E extends Throwable> {

        void accept(T t) throws E;
    }
}
