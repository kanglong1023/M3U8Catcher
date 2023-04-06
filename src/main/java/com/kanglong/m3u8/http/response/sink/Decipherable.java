package com.kanglong.m3u8.http.response.sink;

import com.kanglong.m3u8.http.DecryptionKey;
import com.kanglong.m3u8.util.ByteBufferUtil;
import com.kanglong.m3u8.util.Preconditions;
import com.kanglong.m3u8.util.Utils;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import java.io.IOException;
import java.nio.ByteBuffer;

@Slf4j
public class Decipherable implements SinkLifeCycle {

    private final String identity;

    private final DecryptionKey decryptionKey;

    private Cipher cipher;

    private ByteBuffer heapBuffer;

    private BufferWrapper presetBuffer;

    public Decipherable(String identity, DecryptionKey decryptionKey) {
        this.identity = identity;
        this.decryptionKey = decryptionKey;
    }

    @Override
    public void init(boolean reInit) throws IOException {
        if (reInit) {
            Cipher cipher = this.cipher;
            if (null != cipher) {
                try {
                    cipher.doFinal();
                } catch (Exception ignored) {
                }
                this.cipher = null;
                if (log.isDebugEnabled()) {
                    log.debug("reInit, reset cipher: {}", identity);
                }
            }
            ByteBuffer heapBuffer = this.heapBuffer;
            if (null != heapBuffer) {
                heapBuffer.clear();
            }
        }
        this.cipher = this.decryptionKey.getAndInitCipher();
    }

    public Cipher getCipher() {
        return Preconditions.checkNotNull(this.cipher, "not initialized");
    }

    public void presetOutputBuffer(BufferWrapper bufferWrapper) {
        if (null == bufferWrapper || null != this.presetBuffer) {
            return;
        }
        ByteBuffer byteBuffer = bufferWrapper.unWrap();
        if (!byteBuffer.hasArray()) {
            return;
        }
        this.heapBuffer = byteBuffer;
        this.presetBuffer = bufferWrapper;
    }

    private ByteBuffer getOutputBuffer(int outputSize) {
        ByteBuffer heapBuffer = this.heapBuffer;
        if (null != heapBuffer) {
            if (heapBuffer.hasArray() && heapBuffer.clear().limit() >= outputSize) {
                return heapBuffer;
            }
            if (null != presetBuffer) {
                presetBuffer.release();
                presetBuffer = null;
            }
        }
        return this.heapBuffer = ByteBufferUtil.allocate(outputSize);
    }

    public ByteBuffer decrypt(Cipher cipher, boolean endData, ByteBuffer byteBuffer) {
        if (!byteBuffer.hasRemaining()) {
            return byteBuffer;
        }

        ByteBuffer outputBuffer = null;
        try {
            int inputSize = byteBuffer.remaining();
            int outputSize = cipher.getOutputSize(inputSize);
            outputBuffer = getOutputBuffer(outputSize);
            if (endData) {
                cipher.doFinal(byteBuffer, outputBuffer);
            } else {
                cipher.update(byteBuffer, outputBuffer);
            }
            outputBuffer.flip();
        } catch (Exception ex) {
            Utils.sneakyThrow(ex);
        }
        return outputBuffer;
    }

    @Override
    public void dispose() throws IOException {
        if (null != this.heapBuffer) {
            this.heapBuffer.clear();
            this.heapBuffer = null;
        }
        if (null != this.presetBuffer) {
            this.presetBuffer.release();
            this.presetBuffer = null;
        }
    }
}
