package io.github.kanglong1023.m3u8.util.function;

import java.util.Objects;
import java.util.function.Consumer;

/**
 *
 */
@FunctionalInterface
public interface CheckedConsumer<T> {

    static <T> CheckedConsumer<T> of(CheckedConsumer<T> methodReference) {
        return methodReference;
    }

    void accept(T t) throws Throwable;

    default Consumer<T> unchecked() {
        return t -> {
            try {
                accept(t);
            } catch (Throwable throwable) {
                sneakyThrow(throwable);
            }
        };
    }

    default CheckedConsumer<T> andThen(CheckedConsumer<? super T> after) {
        Objects.requireNonNull(after, "after is null");
        return (T t) -> {
            accept(t);
            after.accept(t);
        };
    }

    @SuppressWarnings("unchecked")
    static <T extends Throwable, R> R sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }

}
