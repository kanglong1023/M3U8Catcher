package io.github.kanglong1023.m3u8.http.response.sink;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface SinkHandler {

    void init(List<CompletableFuture<Void>> sinkFutures, boolean reInit) throws IOException;

    void doSink(ByteBuffer data, boolean endData) throws IOException;

    void dispose() throws IOException;

}


