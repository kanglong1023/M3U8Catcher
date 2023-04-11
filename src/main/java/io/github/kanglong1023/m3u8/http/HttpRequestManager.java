package io.github.kanglong1023.m3u8.http;

import io.github.kanglong1023.m3u8.http.component.CustomHttpRequestRetryStrategy;
import io.github.kanglong1023.m3u8.http.config.HttpRequestConfig;
import io.github.kanglong1023.m3u8.http.config.HttpRequestManagerConfig;
import io.github.kanglong1023.m3u8.http.pool.ScopedIdentity;
import io.github.kanglong1023.m3u8.http.response.BytesResponseConsumer;
import io.github.kanglong1023.m3u8.http.response.FileDownloadOptions;
import io.github.kanglong1023.m3u8.http.response.FileDownloadPostProcessor;
import io.github.kanglong1023.m3u8.http.response.FileResponseConsumer;
import io.github.kanglong1023.m3u8.http.response.sink.*;
import io.github.kanglong1023.m3u8.util.Preconditions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.kanglong1023.m3u8.util.Utils.genIdentity;
import static org.apache.commons.lang3.ObjectUtils.allNotNull;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

@Slf4j
public class HttpRequestManager {

    private final AtomicReference<State> state;

    private final HttpManagerResource managerResource;

    public HttpRequestManager() {
        this(null);
    }

    public HttpRequestManager(HttpRequestManagerConfig managerConfig) {
        if (null == managerConfig) {
            managerConfig = HttpRequestManagerConfig.DEFAULT;
        }
        this.state = new AtomicReference<>(State.ACTIVE);
        this.managerResource = new HttpManagerResource(managerConfig);
        log.info("managerConfig={}", managerConfig);
    }

    public static HttpRequestManager getInstance() {
        return new HttpRequestManager();
    }

    public static HttpRequestManager getInstance(HttpRequestManagerConfig managerConfig) {
        return new HttpRequestManager(managerConfig);
    }

    public void shutdown() throws Exception {
        if (this.state.compareAndSet(State.ACTIVE, State.SHUTDOWN)) {

            log.info("shutdown HttpRequestManager");

            this.managerResource.shutdown();
        }
    }

    public void awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        this.managerResource.awaitTermination(timeout, unit);
    }

    public CompletableFuture<ByteBuffer> getBytes(URI uri, HttpRequestConfig requestConfig) {

        checkState();

        Preconditions.checkNotNull(uri);

        String uriIdentity = genIdentity(uri);

        HttpClientContext clientContext = HttpClientContext.create();

        AsyncRequestProducer requestProducer = SimpleRequestProducer.create(getRequest(uri, requestConfig, clientContext));

        BytesResponseConsumer responseConsumer = new BytesResponseConsumer(uriIdentity);

        CompletableFuture<ByteBuffer> future = new CompletableFuture<>();

        FutureCallback<ByteBuffer> futureCallback = new FutureCallback<ByteBuffer>() {

            @Override
            public void completed(ByteBuffer result) {
                future.complete(result);
            }

            @Override
            public void failed(Exception ex) {
                future.completeExceptionally(ex);
            }

            @Override
            public void cancelled() {
                future.cancel(false);
            }
        };

        getHttpClient().execute(requestProducer, responseConsumer, clientContext, futureCallback);

        return future;
    }

    public CompletableFuture<Path> downloadFile(URI uri, Path filePath) {
        return downloadFile(uri, filePath, null, null, null);
    }

    public CompletableFuture<Path> downloadFile(URI uri, Path filePath, String parentIdentity,
                                                HttpRequestConfig requestConfig,
                                                FileDownloadPostProcessor fileDownloadPostProcessor) {
        return downloadFile(uri, filePath, parentIdentity, requestConfig, null, fileDownloadPostProcessor);
    }

    public CompletableFuture<Path> downloadFile(URI uri, Path filePath, String parentIdentity,
                                                HttpRequestConfig requestConfig, DecryptionKey decryptionKey,
                                                FileDownloadPostProcessor fileDownloadPostProcessor) {
        return downloadFile(uri, filePath, parentIdentity, null, decryptionKey, requestConfig, fileDownloadPostProcessor);
    }

    public CompletableFuture<Path> downloadFile(URI uri, Path filePath, String parentIdentity,
                                                FileDownloadOptions options, HttpRequestConfig requestConfig,
                                                FileDownloadPostProcessor fileDownloadPostProcessor) {
        return downloadFile(uri, filePath, parentIdentity,
                options, null, requestConfig, fileDownloadPostProcessor);
    }

    public CompletableFuture<Path> downloadFile(URI uri, Path filePath,
                                                String parentIdentity, FileDownloadOptions options,
                                                DecryptionKey decryptionKey, HttpRequestConfig requestConfig,
                                                FileDownloadPostProcessor fileDownloadPostProcessor) {
        ScopedIdentity parentScope = null;
        String uriIdentity = genIdentity(uri);
        if (StringUtils.isNotBlank(parentIdentity)) {
            parentScope = new ScopedIdentity(parentIdentity);
        }

        ScopedIdentity scopedIdentity = new ScopedIdentity(uriIdentity, parentScope);
        String identity = scopedIdentity.getFullIdentity();

        AsyncSink asyncSink = null;
        BufferProvider bufferProvider;
        Decipherable decipherable = null;
        options = FileDownloadOptions.defaultOptionsIfNull(options);

        if (options.ifAsyncSink()) {
            asyncSink = new AsyncSink(identity, r -> getExecutor().execute(r));
        }

        if (null != decryptionKey) {
            decipherable = new Decipherable(identity, decryptionKey);
        }

        if (options.useBufferPool()) {
            ByteBufferPool byteBufferPool;
            if (null != decipherable) {
                byteBufferPool = getHeapBufferPool();
            } else {
                byteBufferPool = getDirectBufferPool();
            }

            if (null != asyncSink) {
                bufferProvider = BufferProvider.coteriePoolBuffer(byteBufferPool, scopedIdentity);
            } else {
                bufferProvider = BufferProvider.localPoolBuffer(byteBufferPool);
            }
        } else {
            if (null != decipherable) {
                bufferProvider = BufferProvider.plainHeapBuffer(managerResource.bufferSize);
            } else {
                bufferProvider = BufferProvider.plainDirectBuffer(managerResource.bufferSize);
            }
        }

        UtilitySinkHandler utilitySinkHandler = new UtilitySinkHandler(filePath, bufferProvider, asyncSink, decipherable);
        return downloadFile(uri, filePath, identity, fileDownloadPostProcessor, utilitySinkHandler, requestConfig);
    }

    public CompletableFuture<Path> downloadFile(URI uri, Path filePath, String identity,
                                                FileDownloadPostProcessor postProcessor,
                                                SinkHandler sinkHandler, HttpRequestConfig requestConfig) {
        checkState();
        Preconditions.checkArgument(allNotNull(uri, filePath, identity, sinkHandler));
        FileDownloadPostProcessor fileDownloadPostProcessor = defaultIfNull(postProcessor, FileDownloadPostProcessor.NOP);

        HttpClientContext clientContext = HttpClientContext.create();
        AsyncRequestProducer requestProducer = SimpleRequestProducer.create(getRequest(uri, requestConfig, clientContext));
        FileResponseConsumer responseConsumer = new FileResponseConsumer(filePath, identity, sinkHandler, fileDownloadPostProcessor);

        CompletableFuture<Path> downloadCompletedFuture = new CompletableFuture<>();
        FutureCallback<Path> futureCallback = new FutureCallback<Path>() {

            @Override
            public void completed(Path result) {
                List<CompletableFuture<Void>> actionFutures = responseConsumer.getSinkFutures();
                actionFutures.removeIf(f -> f.isDone() && !f.isCompletedExceptionally());
                CompletableFuture<Void> future = CompletableFuture.allOf(actionFutures.toArray(new CompletableFuture[0]));
                future.whenComplete((v, th) -> {
                    try {
                        responseConsumer.dispose();
                    } catch (Exception ex) {
                        if (null != th) {
                            th.addSuppressed(ex);
                        } else {
                            th = ex;
                        }
                    }
                    if (null != th) {
                        fileDownloadPostProcessor.afterDownloadFailed();
                        downloadCompletedFuture.completeExceptionally(th);
                        log.error(th.getMessage(), th);
                    } else {
                        fileDownloadPostProcessor.afterDownloadComplete();
                        downloadCompletedFuture.complete(result);
                    }
                });
            }

            @Override
            public void failed(Exception ex) {
                try {
                    responseConsumer.dispose();
                } catch (Exception e) {
                    ex.addSuppressed(e);
                }
                log.error(ex.getMessage(), ex);
                fileDownloadPostProcessor.afterDownloadFailed();
                downloadCompletedFuture.completeExceptionally(ex);
            }

            @Override
            public void cancelled() {
                try {
                    responseConsumer.dispose();
                } catch (Exception ex) {
                    log.error(ex.getMessage(), ex);
                }
                fileDownloadPostProcessor.afterDownloadFailed();
                downloadCompletedFuture.cancel(false);
            }
        };

        getHttpClient().execute(requestProducer, responseConsumer, clientContext, futureCallback);

        return downloadCompletedFuture;
    }

    private void checkState() {
        if (state.get() == State.SHUTDOWN) {
            throw new IllegalStateException("httpRequestManager already shutdown");
        }
    }

    private SimpleHttpRequest getRequest(URI uri, HttpRequestConfig requestConfig, HttpClientContext context) {
        Preconditions.checkNotNull(uri);
        Preconditions.checkNotNull(context);

        SimpleHttpRequest request = SimpleHttpRequest.create(Method.GET, uri);
        if (null == requestConfig) {
            return request;
        }

        Proxy proxy = requestConfig.getProxy();
        if (null != proxy) {
            if (proxy.type() == Proxy.Type.HTTP) {
                if (!(proxy.address() instanceof InetSocketAddress)) {
                    throw new RuntimeException("Unable to handle non-Inet proxy address: " + proxy.address());
                }
                InetSocketAddress isa = (InetSocketAddress) proxy.address();
                HttpHost proxyHost = new HttpHost(null, isa.getAddress(), isa.getHostString(), isa.getPort());

                RequestConfig defaultRequestConfig = getDefaultRequestConfig();
                RequestConfig.Builder builder = null != defaultRequestConfig ? RequestConfig.copy(defaultRequestConfig) : RequestConfig.custom();
                RequestConfig config = builder.setProxy(proxyHost).build();
                request.setConfig(config);
            }
        }

        Integer retryCount = requestConfig.getRetryCount();
        if (null != retryCount) {
            CustomHttpRequestRetryStrategy.setMaxRetries(context, retryCount);
        }

        Map<String, Object> requestHeaderMap = requestConfig.getRequestHeaderMap();
        if (MapUtils.isNotEmpty(requestHeaderMap)) {
            for (Map.Entry<String, Object> entry : requestHeaderMap.entrySet()) {
                request.addHeader(entry.getKey(), entry.getValue());
            }
        }

        return request;
    }

    private ByteBufferPool getDirectBufferPool() {
        return this.managerResource.getDirectBufferPool();
    }

    private ByteBufferPool getHeapBufferPool() {
        return this.managerResource.getHeapBufferPool();
    }

    private Executor getExecutor() {
        return this.managerResource.getExecutor();
    }

    private RequestConfig getDefaultRequestConfig() {
        return this.managerResource.getDefaultRequestConfig();
    }

    private CloseableHttpAsyncClient getHttpClient() {
        return this.managerResource.getHttpClient();
    }

    enum State {
        ACTIVE, SHUTDOWN,
    }

}
