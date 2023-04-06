package com.kanglong.m3u8.core;

import com.kanglong.m3u8.http.DecryptionKey;
import com.kanglong.m3u8.http.HttpRequestManager;
import com.kanglong.m3u8.http.config.HttpRequestConfig;
import com.kanglong.m3u8.http.response.FileDownloadOptions;
import com.kanglong.m3u8.http.response.FileDownloadPostProcessor;
import com.kanglong.m3u8.util.CollUtil;
import com.kanglong.m3u8.util.FutureUtil;
import com.kanglong.m3u8.util.function.CheckedRunnable;
import com.kanglong.m3u8.util.function.Try;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static com.kanglong.m3u8.core.M3u8HttpRequestType.REQ_FOR_TS;
import static com.kanglong.m3u8.core.TsDownloadOptionsSelector.PlainTsDownloadOptionsSelector.optionsSelector;
import static com.kanglong.m3u8.util.FutureUtil.disinterest;
import static com.kanglong.m3u8.util.Preconditions.checkNotNull;
import static com.kanglong.m3u8.util.ThreadUtil.newFixedScheduledThreadPool;
import static com.kanglong.m3u8.util.ThreadUtil.newFixedThreadPool;
import static java.util.Optional.ofNullable;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

@Slf4j
public class M3u8Executor {

    private final ExecutorService executor;

    private final HttpRequestManager requestManager;

    private final ScheduledExecutorService scheduler;

    private final M3u8ExecutorProgress progressScheduler;

    private final TsDownloadOptionsSelector optionsSelector;

    public M3u8Executor(HttpRequestManager requestManager) {
        this(requestManager, null);
    }

    public M3u8Executor(HttpRequestManager requestManager, TsDownloadOptionsSelector optionsSelector) {
        final int queueSize = 1_000;
        final String executorNameFormat = "m3u8-executor";
        final String schedulerNameFormat = "m3u8-scheduler";
        final int nThreads = Runtime.getRuntime().availableProcessors();

        this.requestManager = checkNotNull(requestManager);
        this.progressScheduler = new M3u8ExecutorProgress();
        this.executor = newFixedThreadPool(nThreads, queueSize, executorNameFormat, false);
        this.scheduler = newFixedScheduledThreadPool(1, schedulerNameFormat, true);
        this.optionsSelector = defaultIfNull(optionsSelector, optionsSelector(true, true));

        this.scheduler.scheduleWithFixedDelay(progressScheduler, 1, 1, TimeUnit.SECONDS);

        log.info("{} threads={}, queueSize={}", executorNameFormat, nThreads, queueSize);
    }

    public void shutdownAwaitMills(long awaitMills) {
        log.info("shutdown M3u8Executor");

        Consumer<CheckedRunnable> logExceptionConsumer = r -> Try.run(r).onFailure(th -> log.error(th.getMessage(), th));

        logExceptionConsumer.accept(this.executor::shutdown);

        logExceptionConsumer.accept(this.scheduler::shutdown);

        logExceptionConsumer.accept(this.requestManager::shutdown);

        logExceptionConsumer.accept(() -> this.executor.awaitTermination(awaitMills, TimeUnit.MILLISECONDS));

        logExceptionConsumer.accept(() -> this.scheduler.awaitTermination(awaitMills, TimeUnit.MILLISECONDS));

        logExceptionConsumer.accept(() -> this.requestManager.awaitTermination(awaitMills, TimeUnit.MILLISECONDS));

    }

    public CompletableFuture<Void> execute(List<M3u8Download> downloads) {
        if (CollectionUtils.isEmpty(downloads)) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<?>> futures = CollUtil.newArrayListWithCapacity(downloads.size());
        for (M3u8Download download : downloads) {
            if (null == download) {
                continue;
            }
            futures.add(execute(download));
        }

        return disinterest(FutureUtil.allOfColl(futures));
    }

    public CompletableFuture<M3u8Download> execute(M3u8Download m3u8Download) {

        checkNotNull(m3u8Download);

        CompletableFuture<M3u8Download> future = new CompletableFuture<>();

        this.executor.execute(new M3u8DownloadRunner(m3u8Download, future));

        return future;
    }

    private CompletableFuture<Void> downloadTs(List<TsDownload> tsDownloads, FileDownloadOptions options) {
        tsDownloads = emptyIfNull(tsDownloads);

        List<CompletableFuture<Path>> downloadFileFutureList = CollUtil.newArrayListWithCapacity(tsDownloads.size());

        tsDownloads.forEach(d -> downloadFileFutureList.add(downloadTs(d, options)));

        return disinterest(FutureUtil.allOfColl(downloadFileFutureList));
    }

    private DecryptionKey convertKey(M3u8SecretKey m3u8SecretKey) {
        if (null == m3u8SecretKey || m3u8SecretKey == M3u8SecretKey.NONE
                || Objects.equals(m3u8SecretKey.getMethod(), M3u8SecretKey.NONE.getMethod())) {
            return null;
        }
        return new DecryptionKey(m3u8SecretKey.getKey(), m3u8SecretKey.getMethod(), m3u8SecretKey.getInitVector());
    }

    private CompletableFuture<Path> downloadTs(TsDownload tsDownload, FileDownloadOptions options) {
        URI uri = tsDownload.getUri();
        Path filePath = tsDownload.getFilePath();
        M3u8Download m3u8Download = tsDownload.getM3u8Download();
        DecryptionKey decryptionKey = convertKey(tsDownload.getM3u8SecretKey());
        M3u8DownloadOptions m3u8DownloadOptions = m3u8Download.getM3u8DownloadOptions();
        HttpRequestConfig requestConfig = ofNullable(m3u8DownloadOptions.getM3u8HttpRequestConfigStrategy())
                .map(s -> s.getConfig(REQ_FOR_TS, uri)).orElse(null);

        FileDownloadPostProcessor fileDownloadPostProcessor = new FileDownloadPostProcessor() {

            @Override
            public void startDownload(Long contentLength, boolean reStart) {
                contentLength = defaultIfNull(contentLength, -1L);
                tsDownload.startRead(contentLength, reStart);
            }

            @Override
            public void afterReadBytes(int size, boolean end) {
                tsDownload.readBytes(size, end);
            }

            @Override
            public void afterDownloadComplete() {
                tsDownload.complete();
            }

            @Override
            public void afterDownloadFailed() {
                tsDownload.failed();
            }
        };

        return this.requestManager.downloadFile(uri, filePath, m3u8Download.getIdentity(),
                options, decryptionKey, requestConfig, fileDownloadPostProcessor);

    }

    private BiFunction<URI, HttpRequestConfig, ByteBuffer> bytesResponseGetter() {
        return (uri, cfg) -> {
            try {
                return this.requestManager.getBytes(uri, cfg).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        };
    }

    private class M3u8DownloadRunner implements Runnable {

        final M3u8Download m3u8Download;

        final CompletableFuture<M3u8Download> future;

        public M3u8DownloadRunner(M3u8Download m3u8Download, CompletableFuture<M3u8Download> future) {
            this.future = checkNotNull(future);
            this.m3u8Download = checkNotNull(m3u8Download);
        }

        @Override
        public void run() {
            try {

                // resolve m3u8
                List<TsDownload> tsDownloads = m3u8Download.resolveTsDownloads(bytesResponseGetter());

                FileDownloadOptions options = optionsSelector.getDownloadOptions(m3u8Download, tsDownloads);

                log.info("identity={} downloadOptions={}", m3u8Download.getIdentity(), options);

                m3u8Download.notifyDownloadStart();

                // download ts
                CompletableFuture<Void> downloadTsFuture = downloadTs(tsDownloads, options);

                // process scheduler
                progressScheduler.addM3u8(m3u8Download, downloadTsFuture);

                // merge ts
                downloadTsFuture.whenCompleteAsync((v, th) -> {
                    if (null != th) {
                        log.error(th.getMessage(), th);
                    } else {
                        m3u8Download.mergeIntoVideo();
                    }
                }, executor).whenComplete((v, th) -> {
                    if (null != th) {
                        log.error(th.getMessage(), th);
                        future.completeExceptionally(th);
                    } else {
                        future.complete(m3u8Download);
                    }
                });

            } catch (Throwable th) {
                log.error(th.getMessage(), th);
                future.completeExceptionally(th);
            }
        }

    }

}







