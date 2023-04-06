package com.kanglong.m3u8.core;

import com.kanglong.m3u8.http.config.HttpRequestConfig;
import com.kanglong.m3u8.http.config.PureHttpRequestConfig;

import java.net.Proxy;
import java.net.URI;
import java.util.Collections;
import java.util.Map;

public interface M3u8HttpRequestConfigStrategy {

    HttpRequestConfig getConfig(M3u8HttpRequestType requestType, URI uri);


    class DefaultM3u8HttpRequestConfigStrategy implements M3u8HttpRequestConfigStrategy {

        private final Proxy proxy;

        private final Integer retryCount;

        private final Map<M3u8HttpRequestType, Map<String, Object>> requestTypeHeaderMap;

        public DefaultM3u8HttpRequestConfigStrategy(Proxy proxy,
                                                    Integer retryCount,
                                                    Map<M3u8HttpRequestType, Map<String, Object>> requestTypeHeaderMap) {
            this.proxy = proxy;
            this.retryCount = retryCount;
            this.requestTypeHeaderMap = null == requestTypeHeaderMap ? Collections.emptyMap() : requestTypeHeaderMap;
        }

        @Override
        public HttpRequestConfig getConfig(M3u8HttpRequestType requestType, URI uri) {

            Map<String, Object> headerMap = this.requestTypeHeaderMap.get(requestType);

            return new PureHttpRequestConfig(this.proxy, this.retryCount, headerMap);
        }
    }
}
