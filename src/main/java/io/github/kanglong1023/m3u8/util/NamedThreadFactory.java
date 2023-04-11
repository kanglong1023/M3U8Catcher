package io.github.kanglong1023.m3u8.util;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

public class NamedThreadFactory implements ThreadFactory {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NamedThreadFactory.class);

    private final boolean daemon;

    private final AtomicLong count;

    private final ThreadGroup group;

    private final String namePrefix;

    private final UncaughtExceptionHandler uncaughtExceptionHandler;

    public NamedThreadFactory(String namePrefix) {
        this(false, namePrefix);
    }

    public NamedThreadFactory(boolean daemon, String namePrefix) {
        this(daemon, namePrefix, null);
    }

    public NamedThreadFactory(boolean daemon, String namePrefix, UncaughtExceptionHandler uncaughtExceptionHandler) {
        this(daemon, null, namePrefix, uncaughtExceptionHandler);
    }

    public NamedThreadFactory(boolean daemon, ThreadGroup group, String namePrefix, UncaughtExceptionHandler uncaughtExceptionHandler) {
        if (null == namePrefix || namePrefix.isEmpty()) {
            throw new IllegalArgumentException("namePrefix is blank");
        }

        this.group = group;
        this.daemon = daemon;
        this.namePrefix = namePrefix;
        this.count = new AtomicLong();
        this.uncaughtExceptionHandler = null == uncaughtExceptionHandler ?
                DefaultUncaughtExceptionHandler.INSTANCE : uncaughtExceptionHandler;
    }

    @Override
    public Thread newThread(Runnable runnable) {
        Objects.requireNonNull(runnable);
        Thread thread = new Thread(this.group, runnable, this.namePrefix + "-" + this.count.incrementAndGet());
        thread.setUncaughtExceptionHandler(uncaughtExceptionHandler);
        thread.setDaemon(daemon);

        return thread;
    }

    private static class DefaultUncaughtExceptionHandler implements UncaughtExceptionHandler {

        private static final DefaultUncaughtExceptionHandler INSTANCE = new DefaultUncaughtExceptionHandler();

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            log.error(String.format("Caught an exception in %s", t), e);
        }
    }

}
