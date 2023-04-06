package com.kanglong.m3u8.http.response;

public class FileDownloadOptions {

    private final boolean ifAsyncSink;

    private final boolean useBufferPool;

    public FileDownloadOptions(boolean ifAsyncSink, boolean useBufferPool) {
        this.ifAsyncSink = ifAsyncSink;
        this.useBufferPool = useBufferPool;
    }

    public boolean ifAsyncSink() {
        return ifAsyncSink;
    }

    public boolean useBufferPool() {
        return useBufferPool;
    }

    @Override
    public String toString() {
        return "FileDownloadOptions{" +
                "ifAsyncSink=" + ifAsyncSink +
                ", useBufferPool=" + useBufferPool +
                '}';
    }

    public static FileDownloadOptions getInstance(boolean ifAsyncSink, boolean useBufferPool) {
        return new FileDownloadOptions(ifAsyncSink, useBufferPool);
    }

    public static FileDownloadOptions defaultOptionsIfNull(FileDownloadOptions options) {
        return null == options ? getInstance(false, false) : options;
    }


}
