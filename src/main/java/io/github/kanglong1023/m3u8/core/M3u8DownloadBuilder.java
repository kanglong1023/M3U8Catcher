package io.github.kanglong1023.m3u8.core;

import io.github.kanglong1023.m3u8.core.M3u8DownloadOptions.OptionsForApplyTsCache;
import io.github.kanglong1023.m3u8.core.M3u8HttpRequestConfigStrategy.DefaultM3u8HttpRequestConfigStrategy;
import io.github.kanglong1023.m3u8.util.CollUtil;
import org.apache.commons.collections4.MapUtils;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.kanglong1023.m3u8.util.Preconditions.*;

public final class M3u8DownloadBuilder {

    private URI uri;

    private Proxy proxy;

    private Path workHome;

    private String fileName;

    private Path targetFileDir;

    private Integer retryCount;

    private boolean deleteTsOnComplete;

    private boolean mergeWithoutConvertToMp4;

    private M3u8HttpRequestConfigStrategy specRequestConfigStrategy;

    private OptionsForApplyTsCache optionsForApplyTsCache = OptionsForApplyTsCache.SANITY_CHECK;

    private final List<M3u8DownloadListener> listeners = new ArrayList<>();

    private final Map<M3u8HttpRequestType, Map<String, Object>> requestTypeHeaderMap = CollUtil.newLinkedHashMap();

    M3u8DownloadBuilder() {
    }

    public M3u8DownloadBuilder setUri(URI uri) {
        this.uri = checkNotNull(uri);
        return this;
    }

    public M3u8DownloadBuilder setUri(String m3u8Url) {
        this.uri = URI.create(checkNotBlank(m3u8Url));
        return this;
    }

    public M3u8DownloadBuilder setWorkHome(Path workHome) {
        this.workHome = checkNotNull(workHome);
        return this;
    }

    public M3u8DownloadBuilder setFileName(String fileName) {
        this.fileName = checkNotBlank(fileName);
        return this;
    }

    public M3u8DownloadBuilder setTargetFiletDir(Path targetFileDir) {
        this.targetFileDir = checkNotNull(targetFileDir);
        return this;
    }

    public M3u8DownloadBuilder setRetryCount(int retryCount) {
        this.retryCount = checkNonNegative(retryCount, "retryCount");
        return this;
    }

    public M3u8DownloadBuilder deleteTsOnComplete() {
        this.deleteTsOnComplete = true;
        return this;
    }

    public M3u8DownloadBuilder mergeWithoutConvertToMp4() {
        this.mergeWithoutConvertToMp4 = true;
        return this;
    }

    public M3u8DownloadBuilder startOver() {
        this.optionsForApplyTsCache = OptionsForApplyTsCache.START_OVER;
        return this;
    }

    public M3u8DownloadBuilder forceCacheAssignmentBasedOnFileName() {
        this.optionsForApplyTsCache = OptionsForApplyTsCache.FORCE_APPLY_CACHE_BASED_ON_FILENAME;
        return this;
    }

    public M3u8DownloadBuilder optionsForApplyTsCache(OptionsForApplyTsCache optionsForApplyTsCache) {
        this.optionsForApplyTsCache = checkNotNull(optionsForApplyTsCache);
        return this;
    }

    public M3u8DownloadBuilder setRequestConfigStrategy(M3u8HttpRequestConfigStrategy requestConfigStrategy) {
        this.specRequestConfigStrategy = checkNotNull(requestConfigStrategy);
        return this;
    }

    public M3u8DownloadBuilder addListener(M3u8DownloadListener m3u8DownloadListener) {
        this.listeners.add(checkNotNull(m3u8DownloadListener));
        return this;
    }

    public M3u8DownloadBuilder addHeaderForGetM3u8Content(String headerKey, Object headerVal) {
        return addHttpHeader(M3u8HttpRequestType.REQ_FOR_M3U8_CONTENT, headerKey, headerVal);
    }

    public M3u8DownloadBuilder addHeaderForGetM3u8Content(Map<String, Object> requestHeaderMap) {
        return addHttpHeader(M3u8HttpRequestType.REQ_FOR_M3U8_CONTENT, requestHeaderMap);
    }

    public M3u8DownloadBuilder addHeaderForGetVariantPlaylist(String headerKey, Object headerVal) {
        return addHttpHeader(M3u8HttpRequestType.REQ_FOR_VARIANT_PLAYLIST, headerKey, headerVal);
    }

    public M3u8DownloadBuilder addHeaderForGetVariantPlaylist(Map<String, Object> requestHeaderMap) {
        return addHttpHeader(M3u8HttpRequestType.REQ_FOR_VARIANT_PLAYLIST, requestHeaderMap);
    }

    public M3u8DownloadBuilder addHeaderForGetKey(String headerKey, Object headerVal) {
        return addHttpHeader(M3u8HttpRequestType.REQ_FOR_KEY, headerKey, headerVal);
    }

    public M3u8DownloadBuilder addHeaderForGeKey(Map<String, Object> requestHeaderMap) {
        return addHttpHeader(M3u8HttpRequestType.REQ_FOR_KEY, requestHeaderMap);
    }

    public M3u8DownloadBuilder addHeaderForGetTs(String headerKey, Object headerVal) {
        return addHttpHeader(M3u8HttpRequestType.REQ_FOR_TS, headerKey, headerVal);
    }

    public M3u8DownloadBuilder addHeaderForGetTs(Map<String, Object> requestHeaderMap) {
        return addHttpHeader(M3u8HttpRequestType.REQ_FOR_TS, requestHeaderMap);
    }

    public M3u8DownloadBuilder addHttpHeader(M3u8HttpRequestType requestType, String headerKey, Object headerVal) {
        checkNotNull(headerVal);
        checkNotBlank(headerKey);
        checkNotNull(requestType);
        this.requestTypeHeaderMap.computeIfAbsent(requestType, k -> CollUtil.newLinkedHashMap()).put(headerKey, headerVal);
        return this;
    }

    public M3u8DownloadBuilder addHttpHeader(M3u8HttpRequestType requestType, Map<String, Object> requestHeaderMap) {
        if (MapUtils.isNotEmpty(requestHeaderMap)) {
            checkNotNull(requestType);
            this.requestTypeHeaderMap.computeIfAbsent(requestType, k -> CollUtil.newLinkedHashMap()).putAll(requestHeaderMap);
        }
        return this;
    }

    public M3u8DownloadBuilder addHttpHeader(String headerKey, Object headerVal) {
        for (M3u8HttpRequestType requestType : M3u8HttpRequestType.values()) {
            addHttpHeader(requestType, headerKey, headerVal);
        }
        return this;
    }

    public M3u8DownloadBuilder addHttpHeader(Map<String, Object> requestHeaderMap) {
        if (MapUtils.isNotEmpty(requestHeaderMap)) {
            for (M3u8HttpRequestType requestType : M3u8HttpRequestType.values()) {
                addHttpHeader(requestType, requestHeaderMap);
            }
        }
        return this;
    }

    public M3u8DownloadBuilder setProxy(int port) {
        return this.setProxy("127.0.0.1", port);
    }

    public M3u8DownloadBuilder setProxy(String address, int port) {
        return this.setProxy(Proxy.Type.HTTP, address, port);
    }

    public M3u8DownloadBuilder setProxy(Proxy.Type type, String address, int port) {
        this.proxy = new Proxy(type, new InetSocketAddress(address, checkPositive(port, "port")));
        return this;
    }

    public M3u8Download build() {

        M3u8HttpRequestConfigStrategy configStrategy = this.specRequestConfigStrategy;
        if (null == configStrategy) {
            configStrategy = new DefaultM3u8HttpRequestConfigStrategy(this.proxy, this.retryCount, this.requestTypeHeaderMap);
        }

        M3u8DownloadOptions options = new M3u8DownloadOptions(this.deleteTsOnComplete,
                this.mergeWithoutConvertToMp4, optionsForApplyTsCache, configStrategy);

        return new M3u8Download(uri, fileName, workHome, targetFileDir, listeners, options);
    }

}
