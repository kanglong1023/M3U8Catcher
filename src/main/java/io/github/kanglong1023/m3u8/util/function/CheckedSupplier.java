package io.github.kanglong1023.m3u8.util.function;

import java.io.Serializable;

/**
 *
 */
@FunctionalInterface
public interface CheckedSupplier<R> extends Serializable {

    long serialVersionUID = 1L;

    R get() throws Throwable;

    static <R> CheckedSupplier<R> constant(R value) {
        return () -> value;
    }

    static <R> CheckedSupplier<R> of(CheckedSupplier<R> methodReference) {
        return methodReference;
    }

    @SuppressWarnings("unchecked")
    static <R> CheckedSupplier<R> narrow(CheckedSupplier<? extends R> f) {
        return (CheckedSupplier<R>) f;
    }

}
