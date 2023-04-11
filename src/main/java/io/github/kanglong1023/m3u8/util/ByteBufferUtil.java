package io.github.kanglong1023.m3u8.util;

import java.nio.ByteBuffer;

public final class ByteBufferUtil {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ByteBufferUtil.class);

    private ByteBufferUtil() {
    }

    public static ByteBuffer allocateDirect(final int bufferSize) {
        ByteBuffer buffer;
        try {
            buffer = ByteBuffer.allocateDirect(bufferSize);
        } catch (OutOfMemoryError error) {
            // note: -XX:MaxDirectMemorySize=<size>
            log.warn("allocateDirect OOM, {}: {}", error.getMessage(), Utils.getPreviousStackTrace(1));
            // if OOM occurs, have to take the second best
            buffer = allocate(bufferSize);

        }
        return buffer;
    }

    public static ByteBuffer allocate(final int bufferSize) {
        return ByteBuffer.allocate(bufferSize);
    }

}
