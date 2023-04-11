package io.github.kanglong1023.m3u8.util;

import java.util.concurrent.*;

public final class ThreadUtil {

    private ThreadUtil() {
    }

    public static void safeSleep(long mills) {
        try {
            TimeUnit.MILLISECONDS.sleep(mills);
        } catch (InterruptedException ignored) {
        }
    }

    public static ThreadFactory getThreadFactory(String namePrefix, boolean daemon) {
        return new NamedThreadFactory(daemon, namePrefix);
    }

    public static ExecutorService newFixedThreadPool(int nThreads, int queueSize, String nameFormat, boolean daemon) {
        return new ThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(queueSize), getThreadFactory(nameFormat, daemon), callerRunsPolicy(nameFormat));
    }

    public static ScheduledExecutorService newFixedScheduledThreadPool(int nThreads, String nameFormat, boolean daemon) {
        return new ScheduledThreadPoolExecutor(nThreads, getThreadFactory(nameFormat, daemon));
    }

    public static ThreadPoolExecutor.CallerRunsPolicy callerRunsPolicy(String identity) {

        return new ThreadPoolExecutor.CallerRunsPolicy() {

            private final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ThreadPoolExecutor.CallerRunsPolicy.class);

            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                log.warn("{} rejected execution: now caller runs", identity);
                super.rejectedExecution(r, e);
            }
        };
    }

}
