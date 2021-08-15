package com.itiger.persona.common.util;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author tiny.wang
 */
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

    @FunctionalInterface
    public interface FunctionWithException<T, R, E extends Throwable> {

        /**
         * Calls this function.
         *
         * @param value The argument to the function.
         * @return The result of thus supplier.
         * @throws E This function may throw an exception.
         */
        R apply(T value) throws E;
    }

    @FunctionalInterface
    public interface SupplierWithException<R, E extends Throwable> {

        /**
         * Gets the result of this supplier.
         *
         * @return The result of thus supplier.
         * @throws E This function may throw an exception.
         */
        R get() throws E;
    }

    @FunctionalInterface
    public interface ConsumerWithException<T, E extends Throwable> {

        /**
         * Performs this operation on the given argument.
         *
         * @param t the input argument
         * @throws E on errors during consumption
         */
        void accept(T t) throws E;
    }
}
