package io.github.kanglong1023.m3u8.util;

import io.github.kanglong1023.m3u8.core.M3u8Exception;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;

import static java.lang.String.format;

/**
 *
 */
public final class Preconditions {

    private Preconditions() {
    }

    public static void checkArgument(boolean expression) {
        if (!expression) {
            throw new IllegalArgumentException();
        }
    }

    public static void checkArgument(boolean expression, String errorMessage) {
        if (!expression) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    public static void checkArgument(boolean expression, String format, Object... args) {
        if (!expression) {
            throw new IllegalArgumentException(format(format, args));
        }
    }

    public static String checkNotBlank(String reference) {
        if (StringUtils.isBlank(reference)) {
            throw new IllegalArgumentException();
        }
        return reference;
    }

    public static String checkNotBlank(String reference, Object errorMessage) {
        if (StringUtils.isBlank(reference)) {
            throw new IllegalArgumentException(String.valueOf(errorMessage));
        }
        return reference;
    }

    public static String checkNotBlank(String reference, String errorMessageTemplate, Object... errorMessageArgs) {
        if (StringUtils.isBlank(reference)) {
            throw new IllegalArgumentException(format(errorMessageTemplate, errorMessageArgs));
        }
        return reference;
    }

    public static <T> Collection<T> checkNotEmpty(Collection<T> coll) {
        if (null == coll || coll.isEmpty()) {
            throw new IllegalArgumentException();
        }
        return coll;
    }

    public static <T> Collection<T> checkNotEmpty(Collection<T> coll, Object errorMessage) {
        if (null == coll || coll.isEmpty()) {
            throw new IllegalArgumentException(String.valueOf(errorMessage));
        }
        return coll;
    }

    public static <T> Collection<T> checkNotEmpty(Collection<T> coll, String errorMessageTemplate, Object... errorMessageArgs) {
        if (null == coll || coll.isEmpty()) {
            throw new IllegalArgumentException(format(errorMessageTemplate, errorMessageArgs));
        }
        return coll;
    }

    public static <T> T checkNotNull(T reference) {
        if (reference == null) {
            throw new IllegalArgumentException();
        }
        return reference;
    }

    public static <T> T checkNotNull(T reference, Object errorMessage) {
        if (reference == null) {
            throw new IllegalArgumentException(String.valueOf(errorMessage));
        }
        return reference;
    }

    public static <T> T checkNotNull(T reference, String errorMessageTemplate, Object... errorMessageArgs) {
        if (reference == null) {
            throw new IllegalArgumentException(format(errorMessageTemplate, errorMessageArgs));
        }
        return reference;
    }

    public static int checkPositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive but was: " + value);
        }
        return value;
    }

    public static long checkPositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive but was: " + value);
        }
        return value;
    }

    public static int checkNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " cannot be negative but was: " + value);
        }
        return value;
    }

    public static long checkNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " cannot be negative but was: " + value);
        }
        return value;
    }

    public static void checkState(boolean expression) {
        if (!expression) {
            throw new IllegalStateException();
        }
    }

    public static void checkState(boolean expression, String errorMessage) {
        if (!expression) {
            throw new IllegalStateException(errorMessage);
        }
    }

    public static void checkState(boolean expression, String errorMessageTemplate, Object... errorMessageArgs) {
        if (!expression) {
            throw new IllegalStateException(format(errorMessageTemplate, errorMessageArgs));
        }
    }

    public static void m3u8Check(boolean expression) {
        if (!expression) {
            throw new M3u8Exception();
        }
    }

    public static void m3u8Check(boolean expression, String errorMessage) {
        if (!expression) {
            throw new M3u8Exception(errorMessage);
        }
    }

    public static void m3u8Check(boolean expression, String format, Object... args) {
        if (!expression) {
            throw new M3u8Exception(format(format, args));
        }
    }

    public static <T> T m3u8CheckNotNull(T reference) {
        if (reference == null) {
            throw new M3u8Exception();
        }
        return reference;
    }

    public static <T> T m3u8CheckNotNull(T reference, Object errorMessage) {
        if (reference == null) {
            throw new M3u8Exception(String.valueOf(errorMessage));
        }
        return reference;
    }

    public static <T> T m3u8CheckNotNull(T reference, String errorMessageTemplate, Object... errorMessageArgs) {
        if (reference == null) {
            throw new M3u8Exception(format(errorMessageTemplate, errorMessageArgs));
        }
        return reference;
    }

    public static void m3u8Exception(String errorMessage) {
        throw new M3u8Exception(errorMessage);
    }

    public static void m3u8Exception(String format, Object... args) {
        throw new M3u8Exception(format(format, args));
    }

    public static void m3u8Exception(String errorMessage, Throwable cause) {
        throw new M3u8Exception(errorMessage, cause);
    }

    public static void m3u8Exception(Throwable cause, String format, Object... args) {
        throw new M3u8Exception(format(format, args), cause);
    }

}
