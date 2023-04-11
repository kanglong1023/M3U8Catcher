package io.github.kanglong1023.m3u8.http.config;

import java.net.Proxy;
import java.util.Map;

public interface HttpRequestConfig {

    Proxy getProxy();

    Integer getRetryCount();

    Map<String, Object> getRequestHeaderMap();

}
