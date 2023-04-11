package io.github.kanglong1023.m3u8.http.config;

import io.github.kanglong1023.m3u8.http.pool.PoolConfig;
import io.github.kanglong1023.m3u8.util.Preconditions;
import io.github.kanglong1023.m3u8.util.Utils;

import java.util.concurrent.TimeUnit;

public final class HttpRequestManagerConfig {

    public static final HttpRequestManagerConfig DEFAULT = custom().build();

    private final String userAgent;

    private final int ioThreads;

    private final int maxConnTotal;

    private final int maxConnPerRoute;

    private final int executorThreads;

    private final int defaultMaxRetries;

    private final long selectIntervalMills;

    private final long socketTimeoutMills;

    private final long connectTimeoutMills;

    private final long connectionMaxIdleMills;

    private final boolean overrideSystemProxy;

    private final PoolConfig objectPoolConfig;

    private final long defaultRetryIntervalMills;

    private final long connectionRequestTimeoutMills;

    private HttpRequestManagerConfig(String userAgent, int ioThreads,
                                     int maxConnTotal, int maxConnPerRoute,
                                     int executorThreads, int defaultMaxRetries,
                                     long selectIntervalMills, long socketTimeoutMills,
                                     long connectTimeoutMills, long connectionMaxIdleMills,
                                     boolean overrideSystemProxy, PoolConfig objectPoolConfig,
                                     long defaultRetryIntervalMills, long connectionRequestTimeoutMills) {
        this.userAgent = userAgent;
        this.ioThreads = ioThreads;
        this.maxConnTotal = maxConnTotal;
        this.maxConnPerRoute = maxConnPerRoute;
        this.executorThreads = executorThreads;
        this.objectPoolConfig = objectPoolConfig;
        this.defaultMaxRetries = defaultMaxRetries;
        this.socketTimeoutMills = socketTimeoutMills;
        this.selectIntervalMills = selectIntervalMills;
        this.connectTimeoutMills = connectTimeoutMills;
        this.overrideSystemProxy = overrideSystemProxy;
        this.connectionMaxIdleMills = connectionMaxIdleMills;
        this.defaultRetryIntervalMills = defaultRetryIntervalMills;
        this.connectionRequestTimeoutMills = connectionRequestTimeoutMills;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public int getIoThreads() {
        return ioThreads;
    }

    public int getMaxConnTotal() {
        return maxConnTotal;
    }

    public int getMaxConnPerRoute() {
        return maxConnPerRoute;
    }

    public int getExecutorThreads() {
        return executorThreads;
    }

    public int getDefaultMaxRetries() {
        return defaultMaxRetries;
    }

    public long getSelectIntervalMills() {
        return selectIntervalMills;
    }

    public long getSocketTimeoutMills() {
        return socketTimeoutMills;
    }

    public long getConnectTimeoutMills() {
        return connectTimeoutMills;
    }

    public long getConnectionMaxIdleMills() {
        return connectionMaxIdleMills;
    }

    public long getDefaultRetryIntervalMills() {
        return defaultRetryIntervalMills;
    }

    public long getConnectionRequestTimeoutMills() {
        return connectionRequestTimeoutMills;
    }

    public boolean overrideSystemProxy() {
        return overrideSystemProxy;
    }

    public PoolConfig getObjectPoolConfig() {
        return objectPoolConfig;
    }

    @Override
    public String toString() {
        return "HttpRequestManagerConfig{" +
                "userAgent='" + userAgent + '\'' +
                ", ioThreads=" + ioThreads +
                ", maxConnTotal=" + maxConnTotal +
                ", maxConnPerRoute=" + maxConnPerRoute +
                ", executorThreads=" + executorThreads +
                ", defaultMaxRetries=" + defaultMaxRetries +
                ", selectIntervalMills=" + selectIntervalMills +
                ", socketTimeoutMills=" + socketTimeoutMills +
                ", connectTimeoutMills=" + connectTimeoutMills +
                ", connectionMaxIdleMills=" + connectionMaxIdleMills +
                ", overrideSystemProxy=" + overrideSystemProxy +
                ", objectPoolConfig=" + objectPoolConfig +
                ", defaultRetryIntervalMills=" + defaultRetryIntervalMills +
                ", connectionRequestTimeoutMills=" + connectionRequestTimeoutMills +
                '}';
    }

    public static Builder custom() {
        return new Builder();
    }

    public static class Builder {

        private String userAgent;

        private int ioThreads;

        private int maxConnTotal;

        private int maxConnPerRoute;

        private int executorThreads;

        private int defaultMaxRetries;

        private long selectIntervalMills;

        private long socketTimeoutMills;

        private long connectTimeoutMills;

        private long connectionMaxIdleMills;

        private boolean overrideSystemProxy;

        private PoolConfig objectPoolConfig;

        private long defaultRetryIntervalMills;

        private long connectionRequestTimeoutMills;

        private int availableProcessors() {
            return Runtime.getRuntime().availableProcessors();
        }

        Builder() {
            super();

            this.maxConnTotal = 1000;
            this.maxConnPerRoute = 50;
            this.executorThreads = 50;
            this.defaultMaxRetries = 5;
            this.selectIntervalMills = 50;
            this.overrideSystemProxy = false;
            this.ioThreads = availableProcessors() * 2;
            this.userAgent = Utils.getDefaultUserAgent();
            this.socketTimeoutMills = TimeUnit.SECONDS.toMillis(5);
            this.connectTimeoutMills = TimeUnit.SECONDS.toMillis(5);
            this.connectionMaxIdleMills = TimeUnit.MINUTES.toMillis(5);
            this.defaultRetryIntervalMills = TimeUnit.SECONDS.toMillis(1);
            this.connectionRequestTimeoutMills = TimeUnit.HOURS.toMillis(2);
        }

        public Builder userAgent(final String userAgent) {
            Preconditions.checkNotBlank(userAgent, "userAgent is blank");
            this.userAgent = userAgent;
            return this;
        }

        public Builder ioThreads(final int ioThreads) {
            Preconditions.checkPositive(ioThreads, "ioThreads");
            this.ioThreads = ioThreads;
            return this;
        }

        public Builder maxConnTotal(final int maxConnTotal) {
            Preconditions.checkPositive(maxConnTotal, "maxConnTotal");
            this.maxConnTotal = maxConnTotal;
            return this;
        }

        public Builder maxConnPerRoute(final int maxConnPerRoute) {
            Preconditions.checkPositive(maxConnPerRoute, "maxConnPerRoute");
            this.maxConnPerRoute = maxConnPerRoute;
            return this;
        }

        public Builder executorThreads(final int executorThreads) {
            Preconditions.checkPositive(executorThreads, "executorThreads");
            this.executorThreads = executorThreads;
            return this;
        }

        public Builder defaultMaxRetries(final int defaultMaxRetries) {
            Preconditions.checkPositive(defaultMaxRetries, "defaultMaxRetries");
            this.defaultMaxRetries = defaultMaxRetries;
            return this;
        }

        public Builder selectIntervalMills(final long selectIntervalMills) {
            Preconditions.checkPositive(selectIntervalMills, "selectIntervalMills");
            this.selectIntervalMills = selectIntervalMills;
            return this;
        }

        public Builder socketTimeoutMills(final long socketTimeoutMills) {
            Preconditions.checkPositive(socketTimeoutMills, "socketTimeoutMills");
            this.socketTimeoutMills = socketTimeoutMills;
            return this;
        }

        public Builder connectTimeoutMills(final long connectTimeoutMills) {
            Preconditions.checkPositive(connectTimeoutMills, "connectTimeoutMills");
            this.connectTimeoutMills = connectTimeoutMills;
            return this;
        }

        public Builder connectionMaxIdleMills(final long connectionMaxIdleMills) {
            Preconditions.checkPositive(connectionMaxIdleMills, "connectionMaxIdleMills");
            this.connectionMaxIdleMills = connectionMaxIdleMills;
            return this;
        }

        public Builder defaultRetryIntervalMills(final long defaultRetryIntervalMills) {
            Preconditions.checkPositive(defaultRetryIntervalMills, "defaultRetryIntervalMills");
            this.defaultRetryIntervalMills = defaultRetryIntervalMills;
            return this;
        }

        public Builder connectionRequestTimeoutMills(final long connectionRequestTimeoutMills) {
            Preconditions.checkPositive(connectionRequestTimeoutMills, "connectionRequestTimeoutMills");
            this.connectionRequestTimeoutMills = connectionRequestTimeoutMills;
            return this;
        }

        public Builder overrideSystemProxy() {
            this.overrideSystemProxy = true;
            return this;
        }

        public Builder overrideSystemProxy(final boolean overrideSystemProxy) {
            this.overrideSystemProxy = overrideSystemProxy;
            return this;
        }

        public Builder objectPoolConfig(final PoolConfig objectPoolConfig) {
            Preconditions.checkNotNull(objectPoolConfig, "objectPoolConfig");
            this.objectPoolConfig = objectPoolConfig;
            return this;
        }

        public HttpRequestManagerConfig build() {
            int globalPoolCount;
            final int ioThreads = this.ioThreads;
            if (ioThreads >= 8) {
                globalPoolCount = ioThreads >= 32 ? 4 : 2;
            } else {
                globalPoolCount = 1;
            }

            PoolConfig objectPoolConfig = this.objectPoolConfig;
            if (null == objectPoolConfig) {
                objectPoolConfig = globalPoolCount == PoolConfig.DEFAULT.globalPoolCount() ? PoolConfig.DEFAULT :
                        PoolConfig.copy(PoolConfig.DEFAULT).globalPoolCount(globalPoolCount).build();
            } else {
                objectPoolConfig = globalPoolCount == objectPoolConfig.globalPoolCount() ? objectPoolConfig :
                        PoolConfig.copy(objectPoolConfig).globalPoolCount(globalPoolCount).build();
            }

            return new HttpRequestManagerConfig(
                    this.userAgent,
                    this.ioThreads,
                    this.maxConnTotal,
                    this.maxConnPerRoute,
                    this.executorThreads,
                    this.defaultMaxRetries,
                    this.selectIntervalMills,
                    this.socketTimeoutMills,
                    this.connectTimeoutMills,
                    this.connectionMaxIdleMills,
                    this.overrideSystemProxy,
                    objectPoolConfig,
                    this.defaultRetryIntervalMills,
                    this.connectionRequestTimeoutMills);
        }
    }

}
