package io.github.kanglong1023.m3u8.http.response;

import io.github.kanglong1023.m3u8.http.component.UnexpectedHttpStatusException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.ByteArrayBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import static io.github.kanglong1023.m3u8.util.Utils.EMPTY_BIN;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_LENGTH;

@Slf4j
public class BytesResponseConsumer implements AsyncResponseConsumer<ByteBuffer> {

    private final String identity;

    private ByteArrayBuffer buffer;

    private FutureCallback<ByteBuffer> futureCallback;

    public BytesResponseConsumer(String identity) {
        this.identity = defaultIfBlank(identity, "bytesConsume");
    }

    @Override
    public void consumeResponse(HttpResponse response, EntityDetails entityDetails,
                                HttpContext context, FutureCallback<ByteBuffer> resultCallback) throws HttpException, IOException {
        this.futureCallback = resultCallback;

        if (null == entityDetails) {
            resultCallback.completed(EMPTY_BIN);
            log.warn("{} entityDetails is null", identity);
            return;
        }

        int code = response.getCode();
        if (code >= HttpStatus.SC_CLIENT_ERROR) {
            UnexpectedHttpStatusException.throwException(format("UnexpectedHttpStatus: %s code=%s", identity, code));
        }

        long contentLength = 8192;
        Header header = response.getFirstHeader(CONTENT_LENGTH);
        if (null != header) {
            String value = header.getValue();
            if (StringUtils.isNumeric(value)) {
                contentLength = Long.parseLong(value);
            }
        }

        ByteArrayBuffer arrayBuffer = null;
        if (null != this.buffer) {
            // retry
            ByteArrayBuffer curBuffer = this.buffer;
            curBuffer.clear();
            if (curBuffer.capacity() >= contentLength) {
                arrayBuffer = curBuffer;
            }
        }

        if (null == arrayBuffer) {
            contentLength = Math.min(contentLength, 16 * 1024);
            arrayBuffer = new ByteArrayBuffer((int) (contentLength));
        }

        this.buffer = arrayBuffer;
    }

    @Override
    public void informationResponse(HttpResponse response, HttpContext context) throws HttpException, IOException {
        log.info("{} get informationResponse: {}", identity, response.getCode());
    }

    @Override
    public void failed(Exception cause) {
    }

    @Override
    public void updateCapacity(CapacityChannel capacityChannel) throws IOException {
        capacityChannel.update(Integer.MAX_VALUE);
    }

    @Override
    public void consume(ByteBuffer src) throws IOException {
        if (src == null) {
            return;
        }
        if (src.hasArray()) {
            buffer.append(src.array(), src.arrayOffset() + src.position(), src.remaining());
            src.position(src.limit());
        } else {
            while (src.hasRemaining()) {
                buffer.append(src.get());
            }
        }
    }

    @Override
    public void streamEnd(List<? extends Header> trailers) throws HttpException, IOException {
        this.futureCallback.completed(ByteBuffer.wrap(buffer.array(), 0, buffer.length()));
    }

    @Override
    public void releaseResources() {
        if (null != buffer) {
            buffer.clear();
            buffer = null;
        }
    }
}
