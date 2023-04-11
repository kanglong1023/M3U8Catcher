package io.github.kanglong1023.m3u8.http.config;

import java.net.Proxy;
import java.util.Map;

import static io.github.kanglong1023.m3u8.util.Preconditions.checkNotNull;

public class PureHttpRequestConfig extends HttpRequestConfigBase {

    public PureHttpRequestConfig(HttpRequestConfig requestConfig) {
        this(checkNotNull(requestConfig).getProxy(), requestConfig.getRetryCount(), requestConfig.getRequestHeaderMap());
    }

    public PureHttpRequestConfig(Proxy proxy, Integer retryCount, Map<String, Object> requestHeaderMap) {
        super(proxy, retryCount, requestHeaderMap);
    }

}
