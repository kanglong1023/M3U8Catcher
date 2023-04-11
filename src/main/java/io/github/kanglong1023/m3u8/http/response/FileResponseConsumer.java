package io.github.kanglong1023.m3u8.http.response;

import io.github.kanglong1023.m3u8.http.component.UnexpectedHttpStatusException;
import io.github.kanglong1023.m3u8.http.response.sink.SinkHandler;
import io.github.kanglong1023.m3u8.util.CollUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.protocol.HttpContext;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static io.github.kanglong1023.m3u8.util.Preconditions.checkNotNull;
import static io.github.kanglong1023.m3u8.util.Utils.EMPTY_BIN;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_LENGTH;

@Slf4j
public class FileResponseConsumer implements AsyncResponseConsumer<Path> {

    private final Path filePath;

    private final String identity;

    private final SinkHandler sinkHandler;

    private final AtomicLong readBytes = new AtomicLong(0);

    private final FileDownloadPostProcessor fileDownloadPostProcessor;

    private final AtomicBoolean started = new AtomicBoolean(false);

    private final CompletableFuture<Void> selfCompleteFuture = new CompletableFuture<>();


    // ------- non-final  ------//
    private Long contentLength;

    private FutureCallback<Path> futureCallback;

    private List<CompletableFuture<Void>> sinkFutures;

    public FileResponseConsumer(Path filePath, String identity,
                                SinkHandler sinkHandler, FileDownloadPostProcessor fileDownloadPostProcessor) {
        identity = defaultIfBlank(identity, format("download %s", filePath.getFileName()));

        this.identity = identity;
        this.filePath = checkNotNull(filePath);
        this.sinkHandler = checkNotNull(sinkHandler);
        this.sinkFutures = CollUtil.newArrayList(selfCompleteFuture);
        this.fileDownloadPostProcessor = checkNotNull(fileDownloadPostProcessor);
    }

    @Override
    public void consumeResponse(HttpResponse response, EntityDetails entityDetails,
                                HttpContext context, FutureCallback<Path> resultCallback) throws HttpException, IOException {
        this.futureCallback = resultCallback;

        if (null == entityDetails) {
            resultCallback.completed(this.filePath);
            log.warn("{} entityDetails is null", identity);
            return;
        }

        int code = response.getCode();
        if (code >= HttpStatus.SC_CLIENT_ERROR) {
            UnexpectedHttpStatusException.throwException(format("UnexpectedHttpStatus: %s code=%s", identity, code));
        }

        Header contentLenHeader = response.getFirstHeader(CONTENT_LENGTH);
        if (null != contentLenHeader) {
            String value = contentLenHeader.getValue();
            if (StringUtils.isNumeric(value)) {
                this.contentLength = Long.parseLong(value);
            }
        }

        if (started.get()) {
            log.warn("consumeResponse retry: identity={}, readBytes={}", identity, readBytes.get());

            this.sinkFutures = CollUtil.newArrayList(selfCompleteFuture);
            sinkHandler.init(this.sinkFutures, true);

            readBytes.set(0);
            fileDownloadPostProcessor.startDownload(contentLength, true);
        } else {

            sinkHandler.init(this.sinkFutures, false);

            started.set(true);
            fileDownloadPostProcessor.startDownload(contentLength, false);
        }
    }

    @Override
    public void informationResponse(HttpResponse response, HttpContext context) throws HttpException, IOException {
        log.info("{} get informationResponse: {}", identity, response.getCode());
    }

    @Override
    public void failed(Exception cause) {
        this.selfCompleteFuture.completeExceptionally(cause);
    }

    @Override
    public void updateCapacity(CapacityChannel capacityChannel) throws IOException {
        capacityChannel.update(Integer.MAX_VALUE);
    }

    @Override
    public void consume(ByteBuffer src) throws IOException {
        final int size;
        if (null == src || (size = src.remaining()) <= 0) {
            return;
        }

        this.sinkFutures.removeIf(f -> f.isDone() && !f.isCompletedExceptionally());
        this.sinkHandler.doSink(src, false);
        this.readBytes.getAndAdd(size);
        this.fileDownloadPostProcessor.afterReadBytes(size, false);
    }

    @Override
    public void streamEnd(List<? extends Header> trailers) throws HttpException, IOException {
        long bytes = this.readBytes.get();
        if (null != contentLength && contentLength != bytes) {
            log.warn("writtenBytes({}) != contentLength({}): {} ", bytes, contentLength, identity);
        }

        this.sinkHandler.doSink(EMPTY_BIN, true);

        this.fileDownloadPostProcessor.afterReadBytes(0, true);
        this.selfCompleteFuture.complete(null);
        this.futureCallback.completed(this.filePath);
    }

    @Override
    public void releaseResources() {
    }

    public void dispose() throws IOException {
        this.sinkHandler.dispose();
    }

    public List<CompletableFuture<Void>> getSinkFutures() {
        return this.sinkFutures;
    }
}
