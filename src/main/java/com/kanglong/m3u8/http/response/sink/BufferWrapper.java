package com.kanglong.m3u8.http.response.sink;

import com.kanglong.m3u8.http.pool.Slot;

import java.nio.ByteBuffer;
import java.util.Objects;

public interface BufferWrapper {

    void release();

    ByteBuffer unWrap();

    static PlainBufferWrapper wrap(ByteBuffer buffer) {
        return new PlainBufferWrapper(buffer);
    }

    static PooledBufferWrapper wrap(Slot<ByteBuffer> slot) {
        return new PooledBufferWrapper(slot);
    }

    class PlainBufferWrapper implements BufferWrapper {

        private final ByteBuffer buffer;

        PlainBufferWrapper(ByteBuffer buffer) {
            this.buffer = Objects.requireNonNull(buffer);
        }

        @Override
        public ByteBuffer unWrap() {
            return this.buffer;
        }

        @Override
        public void release() {
            this.buffer.clear();
        }
    }

    class PooledBufferWrapper implements BufferWrapper {

        private final Slot<ByteBuffer> slot;

        PooledBufferWrapper(Slot<ByteBuffer> slot) {
            this.slot = Objects.requireNonNull(slot);
        }

        @Override
        public void release() {
            this.slot.recycle();
        }

        @Override
        public ByteBuffer unWrap() {
            return this.slot.get();
        }
    }

}
