package com.kanglong.m3u8.http.response.sink;

import com.kanglong.m3u8.http.response.sink.AsyncSink.SinkTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;

import javax.crypto.Cipher;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.kanglong.m3u8.util.Preconditions.checkNotNull;
import static com.kanglong.m3u8.util.Utils.EMPTY_BIN;
import static com.kanglong.m3u8.util.Utils.mapToNullable;
import static java.lang.String.format;

@Slf4j
public class UtilitySinkHandler implements SinkHandler {

    private final Path filePath;

    private final AsyncSink asyncSink;

    private final Decipherable decipherable;

    private final BufferProvider bufferProvider;

    private final List<SinkLifeCycle> sinkLifeCycles;

    // non-final
    private FileChannel fileChannel;

    private BufferWrapper bufferWrapper;

    private List<CompletableFuture<Void>> sinkFutures;

    public UtilitySinkHandler(Path filePath, BufferProvider bufferProvider,
                              AsyncSink asyncSink, Decipherable decipherable) {
        this.filePath = filePath;
        this.asyncSink = asyncSink;
        this.decipherable = decipherable;
        this.bufferProvider = checkNotNull(bufferProvider);
        this.sinkLifeCycles = Stream.of(asyncSink, decipherable, bufferProvider)
                .filter(Objects::nonNull).collect(Collectors.toList());
    }

    @Override
    public void init(List<CompletableFuture<Void>> sinkFutures, boolean reInit) throws IOException {
        for (SinkLifeCycle sinkLifeCycle : this.sinkLifeCycles) {
            sinkLifeCycle.init(reInit);
        }
        if (reInit) {
            if (null != this.fileChannel) {
                try {
                    this.fileChannel.close();
                } catch (Exception ignored) {
                }
            }
            Files.deleteIfExists(this.filePath);
            if (null != bufferWrapper) {
                bufferWrapper.unWrap().clear();
            }
        } else {
            if (null != decipherable) {
                decipherable.presetOutputBuffer(bufferProvider.newBuffer());
            }
        }
        this.sinkFutures = sinkFutures;
        this.fileChannel = FileChannel.open(this.filePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }

    @Override
    public void doSink(ByteBuffer data, boolean endData) throws IOException {
        data = ObjectUtils.defaultIfNull(data, EMPTY_BIN);
        final int size = data.remaining();

        if (size > 0) {
            BufferWrapper bufferWrapper = getCurBuffer();
            ByteBuffer byteBuffer = bufferWrapper.unWrap();
            int remainingSize = size, remainingCapacity = byteBuffer.remaining();
            while (remainingSize >= remainingCapacity) {
                if (data.hasArray()) {
                    byteBuffer.put(data.array(), data.arrayOffset() + data.position(), remainingCapacity);
                    data.position(data.position() + remainingCapacity);
                } else {
                    int s = 0;
                    while (s++ < remainingCapacity) {
                        byteBuffer.put(data.get());
                    }
                }

                byteBuffer.flip();
                write(this.fileChannel, false, bufferWrapper);

                bufferWrapper = nxtBuffer();
                byteBuffer = bufferWrapper.unWrap();
                remainingSize -= remainingCapacity;
                remainingCapacity = byteBuffer.remaining();
            }
            byteBuffer.put(data);
        }
        if (endData) {
            BufferWrapper bufferWrapper = getCurBuffer();
            ByteBuffer byteBuffer = bufferWrapper.unWrap();
            byteBuffer.flip();
            write(this.fileChannel, true, bufferWrapper);
        }
    }

    private void write(FileChannel channel, boolean endData, BufferWrapper bufferWrapper) throws IOException {
        if (null != asyncSink) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            sinkFutures.add(future);
            asyncSink.submitAsyncSinkTask(new AsyncSinkTask(channel, bufferWrapper, future,
                    mapToNullable(decipherable, Decipherable::getCipher), endData, decipherable));
            return;
        }

        try {
            doWrite(channel, bufferWrapper.unWrap(),
                    mapToNullable(decipherable, Decipherable::getCipher), endData, decipherable);
        } finally {
            bufferWrapper.unWrap().clear();
        }
    }

    private BufferWrapper getCurBuffer() {
        if (null == bufferWrapper) {
            bufferWrapper = bufferProvider.newBuffer();
        }
        return bufferWrapper;
    }

    private BufferWrapper nxtBuffer() {
        if (null != asyncSink) {
            bufferWrapper = bufferProvider.newBuffer();
        }
        return bufferWrapper;
    }

    @Override
    public void dispose() throws IOException {
        try {
            if (null != fileChannel) {
                fileChannel.close();
                fileChannel = null;
            }
            if (null != bufferWrapper) {
                bufferWrapper.release();
                bufferWrapper = null;
            }
        } catch (IOException ex) {
            // only print
            log.error("dispose exception: " + filePath.getFileName(), ex);
        }
        for (SinkLifeCycle sinkLifeCycle : this.sinkLifeCycles) {
            sinkLifeCycle.dispose();
        }
    }

    private static class AsyncSinkTask implements SinkTask {

        private final Cipher cipher;

        private final boolean endData;

        private final FileChannel channel;

        private final Decipherable decipherable;

        private final BufferWrapper bufferWrapper;

        private final CompletableFuture<Void> future;

        public AsyncSinkTask(FileChannel channel,
                             BufferWrapper bufferWrapper,
                             CompletableFuture<Void> future,
                             Cipher cipher, boolean endData, Decipherable decipherable) {
            this.cipher = cipher;
            this.endData = endData;
            this.decipherable = decipherable;
            this.future = checkNotNull(future);
            this.channel = checkNotNull(channel);
            this.bufferWrapper = checkNotNull(bufferWrapper);
        }

        @Override
        public boolean endData() {
            return this.endData;
        }

        @Override
        public void doSink() throws IOException {
            try {
                doWrite(channel, bufferWrapper.unWrap(), cipher, endData, decipherable);
            } finally {
                bufferWrapper.release();
            }
        }

        @Override
        public CompletableFuture<Void> completableFuture() {
            return this.future;
        }
    }

    private static void doWrite(FileChannel channel,
                                ByteBuffer byteBuffer,
                                Cipher cipher, boolean endData, Decipherable decipherable) throws IOException {
        checkNotNull(channel);
        checkNotNull(byteBuffer);

        ByteBuffer buffer = byteBuffer;
        if (null != decipherable && null != cipher) {
            buffer = decipherable.decrypt(cipher, endData, byteBuffer);
        }
        if (!buffer.hasRemaining()) {
            return;
        }
        int spin = 1, maxSpin = 20;
        while (true) {
            if (!channel.isOpen()) {
                break;
            }
            channel.write(buffer);

            // all bytes must be written
            if (!buffer.hasRemaining()) {
                break;
            }
            if (++spin > maxSpin) {
                throw new IOException(format("write incomplete, spin=%d", maxSpin));
            }

            buffer.compact();
        }
    }

}
