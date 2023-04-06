package com.kanglong.m3u8.http.config;

import java.net.Proxy;
import java.util.Map;

public interface HttpRequestConfig {

    Proxy getProxy();

    Integer getRetryCount();

    Map<String, Object> getRequestHeaderMap();

}
