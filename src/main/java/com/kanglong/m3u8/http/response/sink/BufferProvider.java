package com.kanglong.m3u8.http.response.sink;

import com.kanglong.m3u8.http.ByteBufferPool;
import com.kanglong.m3u8.http.pool.CoteriePool;
import com.kanglong.m3u8.http.pool.LocalPool;
import com.kanglong.m3u8.http.pool.ScopedIdentity;
import com.kanglong.m3u8.http.pool.Slot;
import com.kanglong.m3u8.util.ByteBufferUtil;
import com.kanglong.m3u8.util.Preconditions;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * note: thread unsafe
 */
public interface BufferProvider extends SinkLifeCycle {

    BufferWrapper newBuffer();

    static BufferProvider plainDirectBuffer(int initBufferSize) {
        return new PlainDirectBufferProvider(initBufferSize);
    }

    static BufferProvider plainHeapBuffer(int initBufferSize) {
        return new PlainHeapBufferProvider(initBufferSize);
    }

    static BufferProvider coteriePoolBuffer(ByteBufferPool bufferPool, ScopedIdentity identity) {
        Preconditions.checkNotNull(identity);
        return new CoteriePoolBufferProvider(identity.getFullIdentity(), () -> bufferPool.allocateCoterie(identity));
    }

    static BufferProvider localPoolBuffer(ByteBufferPool bufferPool) {
        return new LocalPoolBufferProvider(bufferPool::getLocalPool);
    }

    class PlainDirectBufferProvider implements BufferProvider {

        private final int initBufferSize;

        PlainDirectBufferProvider(int initBufferSize) {
            this.initBufferSize = Preconditions.checkPositive(initBufferSize, "initBufferSize");
        }

        @Override
        public BufferWrapper newBuffer() {
            return BufferWrapper.wrap(ByteBufferUtil.allocateDirect(initBufferSize));
        }
    }

    class PlainHeapBufferProvider implements BufferProvider {

        private final int initBufferSize;

        PlainHeapBufferProvider(int initBufferSize) {
            this.initBufferSize = Preconditions.checkPositive(initBufferSize, "initBufferSize");
        }

        @Override
        public BufferWrapper newBuffer() {
            return BufferWrapper.wrap(ByteBufferUtil.allocate(initBufferSize));
        }

    }

    @Slf4j
    class CoteriePoolBufferProvider implements BufferProvider {

        private final String identity;

        private CoteriePool<ByteBuffer> coteriePool;

        private final Supplier<CoteriePool<ByteBuffer>> coteriePoolSupplier;

        CoteriePoolBufferProvider(String identity, Supplier<CoteriePool<ByteBuffer>> coteriePoolSupplier) {
            this.identity = identity;
            this.coteriePoolSupplier = coteriePoolSupplier;
        }

        @Override
        public BufferWrapper newBuffer() {
            if (null == coteriePool) {
                coteriePool = coteriePoolSupplier.get();
                if (log.isDebugEnabled()) {
                    log.debug("init coteriePool: {}", identity);
                }
            }
            Slot<ByteBuffer> slot = coteriePool.allocate();
            return BufferWrapper.wrap(slot);
        }

        @Override
        public void dispose() throws IOException {
            if (null != coteriePool) {
                coteriePool.destroy();
            }
        }
    }

    class LocalPoolBufferProvider implements BufferProvider {

        private LocalPool<ByteBuffer> localPool;

        private final Supplier<LocalPool<ByteBuffer>> localPoolSupplier;

        public LocalPoolBufferProvider(Supplier<LocalPool<ByteBuffer>> localPoolSupplier) {
            this.localPoolSupplier = localPoolSupplier;
        }

        @Override
        public BufferWrapper newBuffer() {
            if (null == localPool) {
                localPool = localPoolSupplier.get();
            }
            Slot<ByteBuffer> slot = localPool.allocate();
            return BufferWrapper.wrap(slot);
        }
    }

}
