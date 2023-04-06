package com.kanglong.m3u8.http.config;

import java.net.Proxy;
import java.util.Collections;
import java.util.Map;

public abstract class HttpRequestConfigBase implements HttpRequestConfig {

    protected final Proxy proxy;

    protected final Integer retryCount;

    protected final Map<String, Object> requestHeaderMap;

    public HttpRequestConfigBase(Proxy proxy, Integer retryCount, Map<String, Object> requestHeaderMap) {
        this.proxy = proxy;
        this.retryCount = retryCount;
        this.requestHeaderMap = null == requestHeaderMap ? Collections.emptyMap() : Collections.unmodifiableMap(requestHeaderMap);
    }

    @Override
    public Proxy getProxy() {
        return this.proxy;
    }

    @Override
    public Integer getRetryCount() {
        return this.retryCount;
    }

    @Override
    public Map<String, Object> getRequestHeaderMap() {
        return this.requestHeaderMap;
    }
}
