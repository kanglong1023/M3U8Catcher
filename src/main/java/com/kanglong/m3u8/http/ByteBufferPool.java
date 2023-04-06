package com.kanglong.m3u8.http;

import com.kanglong.m3u8.http.pool.ObjectPool;
import com.kanglong.m3u8.http.pool.PoolConfig;
import com.kanglong.m3u8.http.pool.PoolMetric;
import com.kanglong.m3u8.http.pool.PooledObjFactory;
import com.kanglong.m3u8.util.ByteBufferUtil;
import com.kanglong.m3u8.util.CollUtil;
import com.kanglong.m3u8.util.Preconditions;
import sun.nio.ch.DirectBuffer;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * A special byteBuffer object pool, not universal.
 */
public final class ByteBufferPool extends ObjectPool<ByteBuffer> {

    public ByteBufferPool(String poolIdentity, PoolConfig poolConfig, PoolMetric poolMetric, PooledObjFactory<ByteBuffer> pooledObjFactory) {
        super(poolIdentity, poolConfig, poolMetric, pooledObjFactory);
    }

    public static ByteBufferPool newDirectBufferPool(final String poolIdentity, final int bufferSize, final PoolConfig poolConfig) {
        Preconditions.checkPositive(bufferSize, "bufferSize");
        PooledObjFactory<ByteBuffer> pooledObjFactory = new ByteBufferPoolFactory() {

            private final Class<?> type = ByteBufferUtil.allocateDirect(0).getClass();

            @SuppressWarnings("unchecked")
            @Override
            public Class<ByteBuffer> getType() {
                return (Class<ByteBuffer>) type;
            }

            @Override
            public ByteBuffer newInstance() {
                return ByteBufferUtil.allocateDirect(bufferSize);
            }
        };

        PoolMetric poolMetric = new PoolMetric(poolConfig);
        return new ByteBufferPool(poolIdentity, poolConfig, poolMetric, pooledObjFactory);
    }

    public static ByteBufferPool newHeapBufferPool(final String poolIdentity, final int bufferSize, final PoolConfig poolConfig) {
        Preconditions.checkPositive(bufferSize, "bufferSize");
        PooledObjFactory<ByteBuffer> pooledObjFactory = new ByteBufferPoolFactory() {

            private final Class<?> type = ByteBufferUtil.allocate(0).getClass();

            @SuppressWarnings("unchecked")
            @Override
            public Class<ByteBuffer> getType() {
                return (Class<ByteBuffer>) type;
            }

            @Override
            public ByteBuffer newInstance() {
                return ByteBufferUtil.allocate(bufferSize);
            }
        };

        PoolMetric poolMetric = new PoolMetric(poolConfig);
        return new ByteBufferPool(poolIdentity, poolConfig, poolMetric, pooledObjFactory);
    }

    private static abstract class ByteBufferPoolFactory implements PooledObjFactory<ByteBuffer> {

        @Override
        public List<ByteBuffer> newInstance(int size) {
            List<ByteBuffer> byteBuffers = CollUtil.newArrayListWithCapacity(size);
            for (int i = 0; i < size; i++) {
                byteBuffers.add(newInstance());
            }
            return byteBuffers;
        }

        @Override
        public boolean validate(ByteBuffer buffer) {
            if (null == buffer) {
                return false;
            }
            if (buffer.hasArray()) {
                return buffer.capacity() > 0;
            } else if (buffer.isDirect()) {
                return 0 != ((DirectBuffer) buffer).address();
            }
            return false;
        }

        @Override
        public void activate(ByteBuffer obj) {
        }

        @Override
        public void passivate(ByteBuffer buffer) {
            if (null != buffer) {
                buffer.clear();
            }
        }

        @Override
        public void free(ByteBuffer buffer) {
            if (null == buffer) {
                return;
            }
            if (buffer.isDirect()) {
                ((DirectBuffer) buffer).cleaner().clean();
            }
            buffer.clear();
        }

        @Override
        public void free(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                free(buffer);
            }
        }
    }

}
