package io.github.kanglong1023.m3u8.http;

import io.github.kanglong1023.m3u8.http.component.ContextualHttpRoutePlanner;
import io.github.kanglong1023.m3u8.http.component.CustomHttpRequestRetryStrategy;
import io.github.kanglong1023.m3u8.http.config.HttpRequestManagerConfig;
import io.github.kanglong1023.m3u8.util.CollUtil;
import io.github.kanglong1023.m3u8.util.Preconditions;
import io.github.kanglong1023.m3u8.util.ThreadUtil;
import io.github.kanglong1023.m3u8.util.function.Try;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultClientConnectionReuseStrategy;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.core5.concurrent.DefaultThreadFactory;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import java.net.ProxySelector;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.github.kanglong1023.m3u8.util.Preconditions.checkNotNull;
import static java.util.Optional.ofNullable;

@Slf4j
final class HttpManagerResource {

    public final int bufferSize = 8192 * 4;

    private final Object lock = new Object();

    private final HttpRequestManagerConfig managerConfig;

    private final List<IOReactorTerminateCallBack> ioReactorTerminateCallBacks;

    private volatile ExecutorService executor;

    private volatile ByteBufferPool heapBufferPool;

    private volatile ByteBufferPool directBufferPool;

    private volatile HttpAsyncClientScope httpAsyncClientScope;

    public HttpManagerResource(HttpRequestManagerConfig managerConfig) {
        this(managerConfig, null);
    }

    public HttpManagerResource(HttpRequestManagerConfig managerConfig,
                               List<IOReactorTerminateCallBack> ioReactorTerminateCallBacks) {
        this.managerConfig = checkNotNull(managerConfig);
        this.ioReactorTerminateCallBacks = CollUtil.newArrayList(ListUtils.emptyIfNull(ioReactorTerminateCallBacks));

        this.ioReactorTerminateCallBacks.add(this::destroyByteBuffLocalPool);
    }

    public void shutdown() throws Exception {
        synchronized (lock) {

            ofNullable(this.executor).ifPresent(ExecutorService::shutdown);

            ofNullable(this.httpAsyncClientScope).ifPresent(s -> Try.run(() -> s.getHttpAsyncClient().close()).get());

            ofNullable(this.heapBufferPool).ifPresent(ByteBufferPool::destroy);

            ofNullable(this.directBufferPool).ifPresent(ByteBufferPool::destroy);
        }

        ofNullable(this.heapBufferPool).filter(a -> managerConfig.getObjectPoolConfig().ifPrintMetric())
                .ifPresent(ByteBufferPool::printMetrics);

        ofNullable(this.directBufferPool).filter(a -> managerConfig.getObjectPoolConfig().ifPrintMetric())
                .ifPresent(ByteBufferPool::printMetrics);
    }

    public void awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        synchronized (lock) {

            ofNullable(this.executor).ifPresent(e -> Try.of(() ->
                    e.awaitTermination(timeout, unit)).get());

            ofNullable(this.httpAsyncClientScope).ifPresent(s -> Try.run(() ->
                    s.getHttpAsyncClient().awaitShutdown(millsTimeValue(unit.toMillis(timeout)))).get());

        }
    }

    private void destroyByteBuffLocalPool() {
        ByteBufferPool heapBufferPool = this.heapBufferPool;
        ByteBufferPool directBufferPool = this.directBufferPool;

        if (null != directBufferPool) {
            directBufferPool.destroyLocalPool();
        }
        if (null != heapBufferPool) {
            heapBufferPool.destroyLocalPool();
        }
    }

    private HttpAsyncClientScope getHttpAsyncClientScope() {
        if (null == httpAsyncClientScope) {
            synchronized (lock) {
                if (null == httpAsyncClientScope) {
                    httpAsyncClientScope = buildClient();
                }
            }
        }
        return httpAsyncClientScope;
    }

    public RequestConfig getDefaultRequestConfig() {
        return getHttpAsyncClientScope().getDefaultRequestConfig();
    }

    public CloseableHttpAsyncClient getHttpClient() {
        CloseableHttpAsyncClient httpAsyncClient = getHttpAsyncClientScope().getHttpAsyncClient();
        if (IOReactorStatus.INACTIVE == httpAsyncClient.getStatus()) {
            httpAsyncClient.start();
        }
        return httpAsyncClient;
    }

    public Executor getExecutor() {
        if (null == executor) {
            synchronized (lock) {
                if (null == executor) {
                    executor = Executors.newFixedThreadPool(managerConfig.getExecutorThreads(),
                            ThreadUtil.getThreadFactory("httpManager-executor", true));
                }
            }
        }
        return executor;
    }

    public ByteBufferPool getDirectBufferPool() {
        if (null == directBufferPool) {
            synchronized (lock) {
                if (null == directBufferPool) {
                    this.directBufferPool = ByteBufferPool.newDirectBufferPool("httpManager", bufferSize,
                            managerConfig.getObjectPoolConfig());
                }
            }
        }
        return directBufferPool;
    }

    public ByteBufferPool getHeapBufferPool() {
        if (null == heapBufferPool) {
            synchronized (lock) {
                if (null == heapBufferPool) {
                    this.heapBufferPool = ByteBufferPool.newHeapBufferPool("httpManager", bufferSize,
                            managerConfig.getObjectPoolConfig());
                }
            }
        }
        return heapBufferPool;
    }

    public HttpRequestManagerConfig getManagerConfig() {
        return managerConfig;
    }

    private Timeout millsTimeOut(long mills) {
        return Timeout.ofMilliseconds(mills);
    }

    private TimeValue millsTimeValue(long mills) {
        return TimeValue.ofMilliseconds(mills);
    }

    private HttpAsyncClientScope buildClient() {

        DefaultSchemePortResolver schemePortResolver = DefaultSchemePortResolver.INSTANCE;

        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setSocketTimeout(millsTimeOut(managerConfig.getSocketTimeoutMills()))
                .setConnectTimeout(millsTimeOut(managerConfig.getConnectTimeoutMills()))
                .build();

        PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
                .useSystemProperties()
                .setConnPoolPolicy(PoolReusePolicy.LIFO)
                .setSchemePortResolver(schemePortResolver)
                .setDefaultConnectionConfig(connectionConfig)
                .setMaxConnTotal(managerConfig.getMaxConnTotal())
                .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.LAX)
                .setMaxConnPerRoute(managerConfig.getMaxConnPerRoute())
                .build();

        IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setTcpNoDelay(true)
                .setSoKeepAlive(true)
                .setIoThreadCount(managerConfig.getIoThreads())
                .setSoTimeout(millsTimeOut(managerConfig.getSocketTimeoutMills()))
                .setSelectInterval(millsTimeValue(managerConfig.getSelectIntervalMills()))
                .build();

        DefaultClientConnectionReuseStrategy reuseStrategy = DefaultClientConnectionReuseStrategy.INSTANCE;

        Callback<Exception> ioReactorExceptionCallback = ex -> log.error("uncaught exception: " + ex.getMessage(), ex);

        CustomHttpRequestRetryStrategy retryStrategy = new CustomHttpRequestRetryStrategy(
                managerConfig.getDefaultMaxRetries(), millsTimeValue(managerConfig.getDefaultRetryIntervalMills()));

        String userAgent = managerConfig.getUserAgent();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(millsTimeOut(managerConfig.getConnectionRequestTimeoutMills()))
                .build();

        HttpRoutePlanner routePlanner;
        if (managerConfig.overrideSystemProxy()) {
            routePlanner = new ContextualHttpRoutePlanner(new DefaultRoutePlanner(schemePortResolver));
        } else {
            ProxySelector defaultProxySelector = AccessController.doPrivileged((PrivilegedAction<ProxySelector>) ProxySelector::getDefault);
            routePlanner = new ContextualHttpRoutePlanner(new SystemDefaultRoutePlanner(schemePortResolver, defaultProxySelector));
        }

        Http1Config http1Config = Http1Config.copy(Http1Config.DEFAULT).setBufferSize(bufferSize).build();

        CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .useSystemProperties()
                .setUserAgent(userAgent)
                .disableCookieManagement()
                .setHttp1Config(http1Config)
                .setRoutePlanner(routePlanner)
                .setRetryStrategy(retryStrategy)
                .setIOReactorConfig(ioReactorConfig)
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(connectionManager)
                .setSchemePortResolver(schemePortResolver)
                .setConnectionReuseStrategy(reuseStrategy)
                .setIoReactorExceptionCallback(ioReactorExceptionCallback)
                .setThreadFactory(CollectionUtils.isEmpty(ioReactorTerminateCallBacks) ?
                        null : new ThreadFactoryWithCallback("httpclient-dispatch", true))
                .evictIdleConnections(millsTimeValue(managerConfig.getConnectionMaxIdleMills()))
                .build();

        client.start();

        return new HttpAsyncClientScope(requestConfig, client);
    }

    private static class HttpAsyncClientScope {

        private final RequestConfig defaultRequestConfig;

        private final CloseableHttpAsyncClient httpAsyncClient;

        public HttpAsyncClientScope(RequestConfig defaultRequestConfig,
                                    CloseableHttpAsyncClient httpAsyncClient) {
            this.httpAsyncClient = checkNotNull(httpAsyncClient);
            this.defaultRequestConfig = checkNotNull(defaultRequestConfig);
        }

        public CloseableHttpAsyncClient getHttpAsyncClient() {
            return this.httpAsyncClient;
        }

        public RequestConfig getDefaultRequestConfig() {
            return this.defaultRequestConfig;
        }

    }

    public interface IOReactorTerminateCallBack {

        void doFinally();

    }

    private static class RunnableWithCallback implements Runnable {

        private final Runnable runnable;

        private final List<IOReactorTerminateCallBack> ioReactorTerminateCallBacks;

        public RunnableWithCallback(Runnable runnable, List<IOReactorTerminateCallBack> ioReactorTerminateCallBacks) {
            this.runnable = Preconditions.checkNotNull(runnable);
            this.ioReactorTerminateCallBacks = ListUtils.emptyIfNull(ioReactorTerminateCallBacks);
        }

        @Override
        public void run() {
            try {
                runnable.run();
            } finally {
                for (IOReactorTerminateCallBack callBack : ioReactorTerminateCallBacks) {
                    try {
                        callBack.doFinally();
                    } catch (Exception ex) {
                        log.error(callBack + " exec doFinally error", ex);
                    }
                }
            }
        }
    }

    private class ThreadFactoryWithCallback extends DefaultThreadFactory {

        public ThreadFactoryWithCallback(String namePrefix) {
            super(namePrefix);
        }

        public ThreadFactoryWithCallback(String namePrefix, boolean daemon) {
            super(namePrefix, daemon);
        }

        public ThreadFactoryWithCallback(String namePrefix, ThreadGroup group, boolean daemon) {
            super(namePrefix, group, daemon);
        }

        @Override
        public Thread newThread(Runnable runnable) {
            RunnableWithCallback target = new RunnableWithCallback(runnable, ioReactorTerminateCallBacks);
            return super.newThread(target);
        }
    }

}
