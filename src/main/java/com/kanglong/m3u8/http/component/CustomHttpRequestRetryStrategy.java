package com.kanglong.m3u8.http.component;

import com.kanglong.m3u8.util.Preconditions;
import com.kanglong.m3u8.util.Utils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;

import java.io.IOException;
import java.net.NoRouteToHostException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.*;

import static java.lang.String.format;

@Slf4j
public class CustomHttpRequestRetryStrategy implements HttpRequestRetryStrategy {

    public static final String maxRetriesKey = "http.maxRetries";

    public static final String retryAfterKey = "http.retryAfter";

    private final int maxRetries;

    private final Set<Integer> retriableCodes;

    private final TimeValue defaultRetryInterval;

    private final Set<Class<? extends IOException>> nonRetriableIOExceptionClasses;

    protected CustomHttpRequestRetryStrategy(int maxRetries,
                                             TimeValue defaultRetryInterval,
                                             Collection<Class<? extends IOException>> clazzes, Collection<Integer> codes) {
        Args.notNegative(maxRetries, "maxRetries");
        Args.notNegative(defaultRetryInterval.getDuration(), "defaultRetryInterval");
        this.maxRetries = maxRetries;
        this.retriableCodes = new HashSet<>(codes);
        this.defaultRetryInterval = defaultRetryInterval;
        this.nonRetriableIOExceptionClasses = new HashSet<>(clazzes);
    }

    public CustomHttpRequestRetryStrategy(int maxRetries,
                                          TimeValue defaultRetryInterval) {
        this(maxRetries, defaultRetryInterval,
                Arrays.asList(ExplicitlyTerminateIOException.class, UnknownHostException.class, NoRouteToHostException.class),
                Arrays.asList(HttpStatus.SC_REQUEST_TIMEOUT, HttpStatus.SC_TOO_MANY_REQUESTS,
                        HttpStatus.SC_BAD_GATEWAY, HttpStatus.SC_GATEWAY_TIMEOUT));
    }

    public CustomHttpRequestRetryStrategy() {
        this(1, TimeValue.ofSeconds(1L));
    }

    @Override
    public boolean retryRequest(HttpRequest request,
                                IOException exception,
                                int execCount, HttpContext context) {
        Args.notNull(request, "request");
        Args.notNull(exception, "exception");

        String identity = genIdentity(request);
        if (StringUtils.isBlank(identity)) {
            identity = "request";
        }

        int finalMaxRetries = getMaxRetries(context);

        if (execCount > finalMaxRetries) {
            log.info("{} retry over max retries({})", identity, finalMaxRetries);
            return false;
        }

        if (this.nonRetriableIOExceptionClasses.contains(exception.getClass())) {
            log.info("{} match nonRetriableIOExceptionClasses({})", identity, exception.getClass().toString());
            return false;
        } else {
            for (Class<? extends IOException> rejectException : this.nonRetriableIOExceptionClasses) {
                if (rejectException.isInstance(exception)) {
                    log.info("{} match nonRetriableIOExceptionClasses({})", identity, exception.getClass().toString());
                    return false;
                }
            }
        }

        if (request instanceof CancellableDependency && ((CancellableDependency) request).isCancelled()) {
            log.info("{} is cancelled", identity);
            return false;
        }

        boolean idempotent = handleAsIdempotent(request);
        if (idempotent) {
            log.info(format("第%d次重试 %s", execCount, identity), exception);
        }
        return idempotent;
    }

    @Override
    public boolean retryRequest(HttpResponse response, int execCount, HttpContext context) {
        Args.notNull(response, "response");

        String identity = null;
        if (null != context) {
            HttpClientContext clientContext = HttpClientContext.adapt(context);
            HttpRequest request = clientContext.getRequest();
            identity = genIdentity(request);
            if (StringUtils.isBlank(identity)) {
                identity = "response";
            }
        }

        int finalMaxRetries = getMaxRetries(context);

        if (execCount > finalMaxRetries) {
            log.info("{} retry over max retries({})", identity, finalMaxRetries);
            return false;
        }

        int responseCode = response.getCode();
        if (responseCode != HttpStatus.SC_SUCCESS) {
            log.warn("{} statusCode={}", identity, responseCode);
        }

        if (this.retriableCodes.contains(response.getCode())) {
            log.info("{} statusCode={} match retriableCodes", identity, responseCode);
            log.info("第{}次重试 {}", execCount, identity);
            return true;
        }

        // eg. HttpStatus.SC_SERVICE_UNAVAILABLE, HttpStatus.SC_REQUEST_TOO_LONG
        TimeValue retryAfter = getRetryAfterFormHeader(response);
        if (TimeValue.isPositive(retryAfter)) {
            if (null != context) {
                context.setAttribute(retryAfterKey, retryAfter);
            }
            log.info("{} retryAfter={}", identity, retryAfter);
            log.info("第{}次重试 {}", execCount, identity);
            return true;
        }

        return false;
    }

    @Override
    public TimeValue getRetryInterval(HttpResponse response, int execCount, HttpContext context) {
        Args.notNull(response, "response");

        TimeValue retryAfter;
        if (null != context) {
            HttpClientContext clientContext = HttpClientContext.adapt(context);
            retryAfter = clientContext.getAttribute(retryAfterKey, TimeValue.class);
            if (null != retryAfter) {
                clientContext.removeAttribute(retryAfterKey);
            }
            if (TimeValue.isPositive(retryAfter)) {
                return retryAfter;
            }
        }

        retryAfter = getRetryAfterFormHeader(response);
        if (TimeValue.isPositive(retryAfter)) {
            return retryAfter;
        }

        return this.defaultRetryInterval;
    }

    private String genIdentity(HttpRequest request) {
        if (null == request) {
            return null;
        }
        try {
            URI uri = request.getUri();
            return Utils.genIdentity(uri);
        } catch (Exception ignored) {
        }
        return null;
    }

    private TimeValue getRetryAfterFormHeader(HttpResponse response) {
        Objects.requireNonNull(response);

        TimeValue retryAfter = null;
        Header header = response.getFirstHeader(HttpHeaders.RETRY_AFTER);
        if (header != null) {
            String value = header.getValue();
            try {
                retryAfter = TimeValue.ofSeconds(Long.parseLong(value));
            } catch (NumberFormatException ignore) {
                Instant retryAfterDate = DateUtils.parseStandardDate(value);
                if (retryAfterDate != null) {
                    retryAfter = TimeValue.ofMilliseconds(retryAfterDate.toEpochMilli() - System.currentTimeMillis());
                }
            }

            if (TimeValue.isPositive(retryAfter)) {
                return retryAfter;
            }
        }
        return null;
    }

    private int getMaxRetries(HttpContext context) {
        Integer maxRetries = null;
        if (null != context) {
            HttpClientContext clientContext = HttpClientContext.adapt(context);
            maxRetries = clientContext.getAttribute(maxRetriesKey, Integer.class);
        }
        return Objects.nonNull(maxRetries) && maxRetries >= 0 ? maxRetries : this.maxRetries;
    }

    public static void setMaxRetries(HttpContext context, int maxRetries) {
        Preconditions.checkNotNull(context).setAttribute(maxRetriesKey, maxRetries);
    }

    protected boolean handleAsIdempotent(HttpRequest request) {
        return Method.isIdempotent(request.getMethod());
    }
}
