package com.kanglong.m3u8.http.response;

public interface FileDownloadPostProcessor {

    /**
     * @param contentLength nullable
     */
    default void startDownload(Long contentLength, boolean reStart) {
    }

    default void afterReadBytes(int size, boolean end) {
    }

    default void afterDownloadComplete() {
    }

    default void afterDownloadFailed() {
    }

    FileDownloadPostProcessor NOP = new FileDownloadPostProcessor() {
    };

}
