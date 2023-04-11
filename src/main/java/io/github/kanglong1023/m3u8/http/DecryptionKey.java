package io.github.kanglong1023.m3u8.http;

import javax.crypto.Cipher;

import static io.github.kanglong1023.m3u8.util.CipherUtil.getAndInitM3u8AESDecryptCipher;
import static io.github.kanglong1023.m3u8.util.Preconditions.checkNotNull;

public class DecryptionKey {

    private final byte[] key;

    private final String method;

    private final byte[] initVector;

    public DecryptionKey(byte[] key, String method, byte[] initVector) {
        this.key = checkNotNull(key);
        this.method = checkNotNull(method);
        this.initVector = checkNotNull(initVector);
    }

    public Cipher getAndInitCipher() {
        return getAndInitM3u8AESDecryptCipher(key, initVector);
    }

}
