package io.github.kanglong1023.m3u8;

import io.github.kanglong1023.m3u8.core.*;
import io.github.kanglong1023.m3u8.http.HttpRequestManager;
import io.github.kanglong1023.m3u8.http.config.HttpRequestManagerConfig;
import io.github.kanglong1023.m3u8.http.pool.PoolConfig;
import io.github.kanglong1023.m3u8.http.response.FileDownloadOptions;
import io.github.kanglong1023.m3u8.util.CollUtil;
import io.github.kanglong1023.m3u8.util.Preconditions;
import io.github.kanglong1023.m3u8.util.ThreadUtil;
import io.github.kanglong1023.m3u8.util.Utils;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

import static io.github.kanglong1023.m3u8.util.Preconditions.checkNotBlank;
import static io.github.kanglong1023.m3u8.util.Preconditions.checkNotNull;
import static io.github.kanglong1023.m3u8.util.Utils.isFileNameTooLong;
import static io.github.kanglong1023.m3u8.util.Utils.md5;

/**
 * helper methods to simplify download m3u8
 */
@Slf4j
public final class M3u8Downloads {

    public static void downloadOneCarryCookie(String url, String cookieString) {
        downloadOne(url, CookieString.as(cookieString));
    }

    public static void downloadOneCarryCookie(String url, String fileNameOrFilePath, String cookieString) {
        downloadOne(url, fileNameOrFilePath, CookieString.as(cookieString));
    }

    public static void downloadOneCarryCookie(String url, String fileName, String saveDir, String cookieString) {
        downloadOne(url, fileName, saveDir, CookieString.as(cookieString));
    }

    public static void downloadOneCarryCookie(URI uri, String fileName, Path saveDir, String cookieString) {
        downloadOne(uri, fileName, saveDir, CookieString.as(cookieString));
    }

    public static void downloadOne(String url, M3u8HttpHeader... headers) {
        downloadOne(url, null, headers);
    }

    public static void downloadOne(String url, String fileNameOrFilePath, M3u8HttpHeader... headers) {
        download(newDownload(url, fileNameOrFilePath, headers));
    }

    public static void downloadOne(String url, String fileName, String saveDir, M3u8HttpHeader... headers) {
        download(newDownload(url, fileName, saveDir, headers));
    }

    public static void downloadOne(URI uri, String fileName, Path saveDir, M3u8HttpHeader... headers) {
        download(newDownload(uri, fileName, saveDir, headers));
    }

    /**
     * like: downloadSeriesFromUrl(url_1, url_2, ..., url_n)
     */
    public static void downloadSeriesFromUrl(String... urls) {
        Preconditions.checkArgument(ArrayUtils.isNotEmpty(urls));
        download(Arrays.stream(urls).map(l -> newDownload(l, null)).toArray(M3u8Download[]::new));
    }

    /**
     * like: downloadSeriesFromUrlInUnitedDir(url_1, url_2, ..., saveDir)
     */
    public static void downloadSeriesFromUrlInUnitedDir(String... urlsAndDir) {
        Preconditions.checkArgument(ArrayUtils.isNotEmpty(urlsAndDir));
        Preconditions.checkArgument(urlsAndDir.length > 1, "must contain saveDir");
        int length = urlsAndDir.length;
        String saveDir = urlsAndDir[length - 1];
        download(Arrays.stream(urlsAndDir, 0, length - 1).map(l -> newDownload(l, null, saveDir)).toArray(M3u8Download[]::new));
    }

    /**
     * like: downloadSeriesFromUrlInUnitedDirCarryCookie(url_1, url_2, ..., saveDir, cookieString)
     */
    public static void downloadSeriesFromUrlInUnitedDirCarryCookie(String... urlsAndDirAndCookie) {
        Preconditions.checkArgument(ArrayUtils.isNotEmpty(urlsAndDirAndCookie));
        Preconditions.checkArgument(urlsAndDirAndCookie.length > 2, "must contain saveDir and cookieString");
        int length = urlsAndDirAndCookie.length;
        String saveDir = urlsAndDirAndCookie[length - 2];
        M3u8HttpHeader cookie = CookieString.as(urlsAndDirAndCookie[length - 1]);
        download(Arrays.stream(urlsAndDirAndCookie, 0, length - 2).map(l -> newDownload(l, null, saveDir, cookie)).toArray(M3u8Download[]::new));
    }

    /**
     * like: downloadSeriesInUnitedDir(url_1, fileName_1, url_2, fileName_2, ..., saveDir)
     */
    public static void downloadSeriesInUnitedDir(String url, String fileName,
                                                 String... dirOrSeriesUrlAndFileName) {
        Preconditions.checkArgument(ArrayUtils.isNotEmpty(dirOrSeriesUrlAndFileName));

        // expect odd number
        int length = dirOrSeriesUrlAndFileName.length;
        Preconditions.checkArgument((length & 1) == 1, "must contain saveDir");

        String saveDir = dirOrSeriesUrlAndFileName[length - 1];

        List<M3u8Download> downloads = CollUtil.newArrayList(newDownload(url, fileName, saveDir));
        for (int i = 0, j = 1; j < length - 1; i += 2, j += 2) {
            String l = dirOrSeriesUrlAndFileName[i];
            String n = dirOrSeriesUrlAndFileName[j];
            downloads.add(newDownload(l, n, saveDir));
        }
        download(downloads);
    }

    /**
     * like: downloadSeriesInUnitedDir(url_1, fileName_1, url_2, fileName_2, ..., saveDir, cookieString)
     */
    public static void downloadSeriesInUnitedDirCarryCookie(String url, String fileName,
                                                            String... dirAndCookieOrSeriesUrlAndFileName) {
        Preconditions.checkArgument(ArrayUtils.isNotEmpty(dirAndCookieOrSeriesUrlAndFileName));

        // expect even number
        int length = dirAndCookieOrSeriesUrlAndFileName.length;
        Preconditions.checkArgument((length & 1) == 0, "must contain saveDir and cookieString");

        String saveDir = dirAndCookieOrSeriesUrlAndFileName[length - 2];
        M3u8HttpHeader cookie = CookieString.as(dirAndCookieOrSeriesUrlAndFileName[length - 1]);

        List<M3u8Download> downloads = CollUtil.newArrayList(newDownload(url, fileName, saveDir, cookie));
        for (int i = 0, j = 1; j < length - 2; i += 2, j += 2) {
            String l = dirAndCookieOrSeriesUrlAndFileName[i];
            String n = dirAndCookieOrSeriesUrlAndFileName[j];
            downloads.add(newDownload(l, n, saveDir, cookie));
        }
        download(downloads);
    }

    public static void download(M3u8Download download) {
        Preconditions.checkNotNull(download);
        download(Collections.singletonList(download));
    }

    public static void download(M3u8Download... downloads) {
        Preconditions.checkArgument(ArrayUtils.isNotEmpty(downloads));
        download(Arrays.asList(downloads));
    }

    public static void download(List<M3u8Download> downloads) {
        HttpRequestManagerConfig managerConfig = HttpRequestManagerConfig.custom()
                .defaultMaxRetries(20)
                .build();
        download(managerConfig, downloads);
    }

    public static void download(HttpRequestManagerConfig managerConfig, M3u8Download... downloads) {
        List<M3u8Download> downloadList = Arrays.asList(downloads);
        download(managerConfig, downloadList);
    }

    public static void download(HttpRequestManagerConfig managerConfig, List<M3u8Download> downloads) {
        FixedDownloadNumberOptionsSelector optionsSelector = new FixedDownloadNumberOptionsSelector(downloads, managerConfig);
        download(managerConfig, optionsSelector, downloads);
    }

    public static void download(HttpRequestManagerConfig managerConfig,
                                TsDownloadOptionsSelector optionsSelector, M3u8Download... downloads) {
        download(managerConfig, optionsSelector, Arrays.asList(downloads));
    }

    public static void download(HttpRequestManagerConfig managerConfig,
                                TsDownloadOptionsSelector optionsSelector, List<M3u8Download> downloads) {
        Preconditions.checkNotEmpty(downloads);

        M3u8Executor m3u8Executor = null;
        try {

            m3u8Executor = startExecutor(managerConfig, optionsSelector);

            m3u8Executor.execute(downloads).join();

        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        } finally {
            ThreadUtil.safeSleep(1000);
            Optional.ofNullable(m3u8Executor).ifPresent(e -> e.shutdownAwaitMills(2000));
        }
    }

    public static M3u8Executor startExecutor() {
        return startExecutor(null);
    }

    public static M3u8Executor startExecutor(HttpRequestManagerConfig managerConfig) {
        return startExecutor(managerConfig, null);
    }

    public static M3u8Executor startExecutor(HttpRequestManagerConfig managerConfig,
                                             TsDownloadOptionsSelector optionsSelector) {
        HttpRequestManager requestManager = HttpRequestManager.getInstance(managerConfig);
        return new M3u8Executor(requestManager, optionsSelector);
    }

    public static M3u8Download newDownload(String url, String fileNameOrFilePath, M3u8HttpHeader... headers) {
        String fileName = null, saveDir = null;
        if (StringUtils.isNotBlank(fileNameOrFilePath)) {
            Path path = Paths.get(fileNameOrFilePath);
            boolean absolute = path.isAbsolute();

            if (absolute && Files.isDirectory(path)) {
                saveDir = fileNameOrFilePath;
            } else if (absolute && Files.isDirectory(path.getParent())) {
                saveDir = path.getParent().toString();
                fileName = path.getFileName().toString();
            } else {
                fileName = path.getFileName().toString();
            }
        }
        return newDownload(url, fileName, saveDir, headers);
    }

    public static M3u8Download newDownload(String url, String fileName, String saveDir, M3u8HttpHeader... headers) {
        URI uri = URI.create(checkNotBlank(url));
        Preconditions.checkArgument(Utils.isValidURL(uri), "invalid url");

        Path saveDirPath;
        if (StringUtils.isNotBlank(saveDir)) {
            saveDirPath = Paths.get(saveDir);
        } else {
            saveDirPath = Paths.get("").toAbsolutePath();
        }
        if (StringUtils.isBlank(fileName)) {
            fileName = Paths.get(uri.getPath()).getFileName().toString();
            String finalFileName = M3u8Download.getFinalFileName(fileName);
            if (isFileNameTooLong(saveDirPath.resolve(finalFileName).toString())) {
                String newFileName = M3u8Download.getFinalFileName(md5(uri.toString()));
                log.info("fileName too long, use {}: {}", newFileName, fileName);
                fileName = newFileName;
            }
        }
        return newDownload(uri, fileName, saveDirPath, headers);
    }

    public static M3u8Download newDownload(URI uri, String fileName, Path saveDir, M3u8HttpHeader... headers) {
        Preconditions.checkNotNull(uri);
        Preconditions.checkNotNull(saveDir);
        Preconditions.checkNotBlank(fileName);

        M3u8DownloadBuilder builder = M3u8Download.builder()
                .setUri(uri)
                .deleteTsOnComplete()
                .setFileName(fileName)
                .setTargetFiletDir(saveDir);

        applyM3u8HttpHeader(builder, headers);

        builder.addListener(new M3u8DownloadListener() {
            private LocalDateTime start;

            @Override
            public void start(M3u8Download m3u8Download) {
                start = LocalDateTime.now();
                log.info("start download:{}", m3u8Download.getFileName());
            }

            @Override
            public void end(M3u8Download m3u8Download) {
                LocalDateTime end = LocalDateTime.now();
                log.info("download finished:{}, cost:{}", m3u8Download.getFileName(), Utils.secondsFormat(Duration.between(start, end).getSeconds()));
            }
        });
        return builder.build();
    }

    public static M3u8DownloadBuilder applyM3u8HttpHeader(M3u8DownloadBuilder builder, M3u8HttpHeader... headers) {
        if (ArrayUtils.isEmpty(headers)) {
            return builder;
        }
        Map<M3u8HttpRequestType, Map<String, Object>> requestTypeHeaderMap = CollUtil.newLinkedHashMap();
        for (M3u8HttpHeader header : headers) {
            if (null == header) {
                continue;
            }
            M3u8HttpRequestType requestType = header.getRequestType();
            if (null == requestType) {
                for (M3u8HttpRequestType type : M3u8HttpRequestType.values()) {
                    requestTypeHeaderMap.computeIfAbsent(type, k -> CollUtil.newLinkedHashMap())
                            .put(header.getName(), header.getValue());
                }
            } else {
                requestTypeHeaderMap.computeIfAbsent(requestType, k -> CollUtil.newLinkedHashMap())
                        .put(header.getName(), header.getValue());
            }
            requestTypeHeaderMap.forEach(builder::addHttpHeader);
        }
        return builder;
    }

    /**
     * fixed number of M3u8Downloads(especially when there's only one) can optimize parameters
     */
    @Slf4j
    private static class FixedDownloadNumberOptionsSelector implements TsDownloadOptionsSelector {

        private final OptionsSnapshot optionsSnapshot;

        private final HttpRequestManagerConfig managerConfig;

        private FixedDownloadNumberOptionsSelector(List<M3u8Download> downloads, HttpRequestManagerConfig managerConfig) {
            checkNotNull(managerConfig);
            downloads = ListUtils.emptyIfNull(downloads);

            final int asyncSinkBoundary = 8;
            int downloadSize = downloads.size();
            if (downloadSize >= asyncSinkBoundary) {
                log.info("downloadSize >= asyncSinkBoundary, use AsyncSink: {}", downloadSize);
                this.optionsSnapshot = new OptionsSnapshot(downloads, true, false);
            } else {
                this.optionsSnapshot = new OptionsSnapshot(downloads);
            }
            this.managerConfig = managerConfig;
        }

        @Override
        public FileDownloadOptions getDownloadOptionsInternal(M3u8Download m3u8Download, List<TsDownload> tsDownloads) {
            OptionsSnapshot optionsSnapshot = this.optionsSnapshot;
            boolean ifAsyncSink = optionsSnapshot.ifAsyncSink, useBufferPool = optionsSnapshot.useBufferPool;
            if (ifAsyncSink && useBufferPool) {
                return FileDownloadOptions.getInstance(true, true);
                // un-record
            }

            synchronized (this) {

                ifAsyncSink = optionsSnapshot.ifAsyncSink;
                useBufferPool = optionsSnapshot.useBufferPool;
                if (ifAsyncSink && useBufferPool) {
                    return FileDownloadOptions.getInstance(true, true);
                    // un-record
                }

                ifAsyncSink = ifAsyncSink || worthAsyncSink(optionsSnapshot.getAllRouteCount(),
                        optionsSnapshot::getInstantReadingAndRemainedCount, m3u8Download, tsDownloads);

                useBufferPool = useBufferPool || worthUseBuffer(
                        optionsSnapshot::getRemainedRouteCount,
                        optionsSnapshot::getRemainedDownloads,
                        () -> optionsSnapshot.getMaxRouteSizeOfRemainedDownloads().size(),
                        m3u8Download, tsDownloads);

                FileDownloadOptions result = FileDownloadOptions.getInstance(ifAsyncSink, useBufferPool);

                optionsSnapshot.record(m3u8Download, result);

                return result;
            }
        }

        private boolean worthAsyncSink(int allRouteCount, LongSupplier instantRemainedTsCount,
                                       M3u8Download m3u8Download, List<TsDownload> tsDownloads) {
            int tsCount = tsDownloads.size();
            String identity = m3u8Download.getIdentity();
            int ioThreads = managerConfig.getIoThreads();
            int maxConnPerRoute = managerConfig.getMaxConnPerRoute();

            int maxConnPerThread = allRouteCount * (int) Math.ceil(maxConnPerRoute * 1.0 / ioThreads);
            boolean threadNetworkIOIsBusy = maxConnPerThread >= 16;
            if (threadNetworkIOIsBusy) {
                log.info("worthAsyncSink because of threadNetworkIOIsBusy, " +
                        "identity={} maxConnPerThread={}", identity, maxConnPerThread);
                return true;
            }

            long remainedTsCountAsLong = instantRemainedTsCount.getAsLong();
            boolean tooManyDiskIOTimes = tsCount + remainedTsCountAsLong >= maxConnPerRoute * 10L;
            if (tooManyDiskIOTimes) {
                log.info("worthAsyncSink because of tooManyDiskIOTimes, " +
                        "identity={} tsCount={} remainedTsCount={}", identity, tsCount, remainedTsCountAsLong);
                return true;
            }

            return false;
        }

        private boolean worthUseBuffer(IntSupplier remainedRouteCount,
                                       IntSupplier remainedDownloadSize,
                                       IntSupplier maxRouteSizeOfRemainedDownloads,
                                       M3u8Download m3u8Download, List<TsDownload> tsDownloads) {
            int tsCount = tsDownloads.size();
            String identity = m3u8Download.getIdentity();
            int maxConnPerRoute = managerConfig.getMaxConnPerRoute();
            PoolConfig poolConfig = managerConfig.getObjectPoolConfig();
            int remainedDownloadSizeAsInt = remainedDownloadSize.getAsInt();

            boolean bufferReusable, cheaperMemory;
            if (remainedDownloadSizeAsInt == 1) {
                int atLeastAllocatedSlots = poolConfig.atLeastAllocatedSlots(maxConnPerRoute);

                bufferReusable = tsCount > maxConnPerRoute;
                cheaperMemory = tsCount > atLeastAllocatedSlots;

                boolean res = bufferReusable && cheaperMemory;
                if (res) {
                    log.info("worthUseBuffer in the last download, identity={} tsCount={} " +
                            "maxConnPerRoute={}, atLeastAllocatedSlots={}", identity, tsCount, maxConnPerRoute, atLeastAllocatedSlots);
                }
                return res;
            }

            int remainedRouteCountAsInt = remainedRouteCount.getAsInt();
            int maxRouteSizeOfRemainedDownloadsAsInt = maxRouteSizeOfRemainedDownloads.getAsInt();

            bufferReusable = tsCount > maxConnPerRoute || maxRouteSizeOfRemainedDownloadsAsInt > 1;
            cheaperMemory = (remainedDownloadSizeAsInt / remainedRouteCountAsInt) >= 2;

            boolean res = bufferReusable && cheaperMemory;
            if (res) {
                log.info("worthUseBuffer, identity={} tsCount={} maxConnPerRoute={}, " +
                                "maxRouteSizeOfRemainedDownloads={}, remainedDownloadSize={}, remainedRouteCount={}",
                        identity, tsCount, maxConnPerRoute, maxRouteSizeOfRemainedDownloadsAsInt, remainedDownloadSizeAsInt, remainedRouteCountAsInt);
            }
            return res;
        }

        private static class OptionsSnapshot {

            private final int allRouteCount;

            private volatile boolean ifAsyncSink;

            private volatile boolean useBufferPool;

            private final List<M3u8Download> allDownloads;

            private final List<M3u8Download> downloadSnapshots;

            private final Map<RouteGroupKey, Set<M3u8Download>> downloadRouteGroup;

            private OptionsSnapshot(List<M3u8Download> allDownloads) {
                this(allDownloads, false, false);
            }

            private OptionsSnapshot(List<M3u8Download> allDownloads,
                                    boolean ifAsyncSink, boolean useBufferPool) {

                // consider master list variantStreamInf ？
                Map<RouteGroupKey, Set<M3u8Download>> downloadRouteGroup = CollUtil.newLinkedHashMap();
                for (M3u8Download download : allDownloads) {
                    RouteGroupKey key = new RouteGroupKey(download.getUri());
                    downloadRouteGroup.computeIfAbsent(key, k -> CollUtil.newHashSet()).add(download);
                }

                this.ifAsyncSink = ifAsyncSink;
                this.useBufferPool = useBufferPool;
                this.downloadRouteGroup = downloadRouteGroup;
                this.allRouteCount = downloadRouteGroup.size();
                this.downloadSnapshots = CollUtil.newArrayList();
                this.allDownloads = Collections.unmodifiableList(CollUtil.newArrayList(allDownloads));
            }

            void record(M3u8Download m3u8Download, FileDownloadOptions options) {
                if (null == options) {
                    return;
                }
                if (!this.ifAsyncSink) {
                    this.ifAsyncSink = options.ifAsyncSink();
                }
                if (!this.useBufferPool) {
                    this.useBufferPool = options.useBufferPool();
                }

                this.downloadSnapshots.add(m3u8Download);

                RouteGroupKey routeGroupKey = new RouteGroupKey(m3u8Download.getUri());
                Set<M3u8Download> m3u8Downloads = this.downloadRouteGroup.get(routeGroupKey);
                if (CollectionUtils.isNotEmpty(m3u8Downloads)) {
                    m3u8Downloads.remove(m3u8Download);
                    if (CollectionUtils.isEmpty(m3u8Downloads)) {
                        this.downloadRouteGroup.remove(routeGroupKey);
                    }
                }
            }

            long getInstantReadingAndRemainedCount() {
                long count = 0;
                Iterator<M3u8Download> iterator = downloadSnapshots.iterator();
                while (iterator.hasNext()) {
                    M3u8Download download = iterator.next();
                    long instantReadingAndRemainedCount = download.getInstantReadingAndRemainedCount();
                    if (instantReadingAndRemainedCount <= 0) {
                        iterator.remove();
                        continue;
                    }
                    count += instantReadingAndRemainedCount;
                }
                return count;
            }

            public int getAllRouteCount() {
                return this.allRouteCount;
            }

            public int getRemainedDownloads() {
                return this.downloadRouteGroup.values().stream()
                        .filter(CollectionUtils::isNotEmpty).mapToInt(Set::size).sum();
            }

            public int getRemainedRouteCount() {
                return this.downloadRouteGroup.entrySet().stream()
                        .filter(e -> CollectionUtils.isNotEmpty(e.getValue())).mapToInt(e -> 1).sum();
            }

            public Set<M3u8Download> getMaxRouteSizeOfRemainedDownloads() {
                return this.downloadRouteGroup.values().stream().max(Comparator.comparing(Set::size)).orElse(Collections.emptySet());
            }

            @EqualsAndHashCode
            private static class RouteGroupKey {

                private final int port;

                private final String host;

                private final String schema;

                private RouteGroupKey(URI uri) {
                    Preconditions.checkNotNull(uri);
                    this.host = uri.getHost();
                    this.port = uri.getPort();
                    this.schema = uri.getScheme();
                }
            }
        }
    }

    public static class CookieString {

        public static M3u8HttpHeader as(String value) {
            return M3u8HttpHeader.as("Cookie", value);
        }

        public static M3u8HttpHeader as(String value, M3u8HttpRequestType requestType) {
            return M3u8HttpHeader.as("Cookie", value, requestType);
        }
    }

    private static class BasicM3u8HttpHeader implements M3u8HttpHeader {

        private final String name;

        private final String value;

        private final M3u8HttpRequestType requestType;

        private BasicM3u8HttpHeader(String name, String value, M3u8HttpRequestType requestType) {
            this.requestType = requestType;
            this.name = checkNotBlank(name);
            this.value = checkNotBlank(value);
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public String getValue() {
            return this.value;
        }

        @Override
        public M3u8HttpRequestType getRequestType() {
            return this.requestType;
        }
    }

    public interface M3u8HttpHeader {

        String getName();

        String getValue();

        M3u8HttpRequestType getRequestType();

        static M3u8HttpHeader as(String name, String value) {
            return as(name, value, M3u8HttpRequestType.REQ_FOR_M3U8_CONTENT);
        }

        static M3u8HttpHeader as(String name, String value, M3u8HttpRequestType requestType) {
            return new BasicM3u8HttpHeader(name, value, requestType);
        }
    }

}
