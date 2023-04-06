package com.kanglong.m3u8.http.component;

import com.kanglong.m3u8.util.Preconditions;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.RouteInfo;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;

public class ContextualHttpRoutePlanner implements HttpRoutePlanner {

    private final HttpRoutePlanner routePlanner;

    public ContextualHttpRoutePlanner(HttpRoutePlanner routePlanner) {
        this.routePlanner = Preconditions.checkNotNull(routePlanner);
    }

    @Override
    public HttpRoute determineRoute(HttpHost target, HttpContext context) throws HttpException {
        HttpClientContext clientContext = HttpClientContext.adapt(context);
        RouteInfo routeInfo = clientContext.getHttpRoute();
        if (routeInfo instanceof HttpRoute) {
            return ((HttpRoute) routeInfo);
        }
        return routePlanner.determineRoute(target, context);
    }
}
