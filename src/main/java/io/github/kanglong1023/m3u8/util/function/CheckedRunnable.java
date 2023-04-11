package io.github.kanglong1023.m3u8.util.function;

/**
 *
 */
@FunctionalInterface
public interface CheckedRunnable {

    void run() throws Throwable;

    default Runnable unchecked() {
        return () -> {
            try {
                run();
            } catch (Throwable throwable) {
                sneakyThrow(throwable);
            }
        };
    }

    static CheckedRunnable of(CheckedRunnable methodReference) {
        return methodReference;
    }

    @SuppressWarnings("unchecked")
    static <T extends Throwable, R> R sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }
}
