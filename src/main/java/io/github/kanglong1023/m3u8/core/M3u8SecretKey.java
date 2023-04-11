package io.github.kanglong1023.m3u8.core;

import io.github.kanglong1023.m3u8.util.Preconditions;

public class M3u8SecretKey {

    public static final M3u8SecretKey NONE = new M3u8SecretKey(null, null, "NONE");

    private final byte[] key;

    private final String method;

    private final byte[] initVector;

    public M3u8SecretKey(byte[] key, byte[] initVector, String method) {
        Preconditions.checkNotNull(method);
        this.key = key;
        this.method = method;
        this.initVector = initVector;
    }

    public byte[] getKey() {
        return this.key;
    }

    public String getMethod() {
        return this.method;
    }

    public byte[] getInitVector() {
        return this.initVector;
    }

    public M3u8SecretKey copy() {
        byte[] newKey = new byte[this.key.length];
        byte[] newInitVector = new byte[this.initVector.length];

        System.arraycopy(this.key, 0, newKey, 0, newKey.length);
        System.arraycopy(this.initVector, 0, newInitVector, 0, newInitVector.length);

        return new M3u8SecretKey(newKey, newInitVector, this.method);
    }

}
