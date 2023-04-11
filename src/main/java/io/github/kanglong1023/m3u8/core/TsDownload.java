package io.github.kanglong1023.m3u8.core;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static io.github.kanglong1023.m3u8.core.TsDownload.TsDownloadStage.*;


@Slf4j
@Getter
public class TsDownload {

    private final URI uri;

    private final Path filePath;

    private final Integer sequence;

    private final Path finalFilePath;

    private final Double durationInSeconds;

    private final M3u8Download m3u8Download;

    private final M3u8SecretKey m3u8SecretKey;

    private final AtomicLong readBytes = new AtomicLong(0);

    private volatile long contentLength = -1;

    private volatile TsDownloadStage downloadStage = NEW;

    public TsDownload(URI uri, Path filePath,
                      Integer sequence, Path finalFilePath,
                      Double durationInSeconds, M3u8Download m3u8Download, M3u8SecretKey m3u8SecretKey) {
        this.uri = uri;
        this.filePath = filePath;
        this.sequence = sequence;
        this.m3u8Download = m3u8Download;
        this.finalFilePath = finalFilePath;
        this.m3u8SecretKey = m3u8SecretKey;
        this.durationInSeconds = durationInSeconds;
    }

    public void complete() {
        this.downloadStage = COMPLETED;
        this.m3u8Download.OnFinishTsDownload(this, false);

        if (this.filePath.equals(finalFilePath)) {
            return;
        }

        if (Files.exists(filePath)) {
            try {
                Files.move(filePath, finalFilePath, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ex) {
                log.error(String.format("move %s to %s error: %s", filePath, finalFilePath, ex.getMessage()), ex);
            }
        }
    }

    public void completeInCache() {
        this.downloadStage = COMPLETED_IN_CACHE;
        this.m3u8Download.OnFinishTsDownload(this, false);
    }

    public void failed() {
        this.downloadStage = FAILED;
        this.m3u8Download.OnFinishTsDownload(this, true);
    }

    public void startRead(long contentLength, boolean reRead) {
        if (contentLength > 0 && contentLength != this.contentLength) {
            this.contentLength = contentLength;
        }
        if (reRead) {
            this.readBytes.set(0);
        } else {
            this.downloadStage = READING;
            this.m3u8Download.startReadTs(this);

        }
    }

    public void readBytes(int size, boolean end) {
        this.readBytes.getAndAdd(size);
        this.m3u8Download.downloadBytes(size);
    }

    public long remainingBytes() {
        if (downloadStage == READING) {
            long contentLength = this.contentLength;
            if (contentLength < 0) {
                return contentLength;
            } else {
                return contentLength - readBytes.get();
            }
        } else if (downloadStage == NEW) {
            return contentLength;
        } else {
            return 0;
        }
    }

    public boolean isNew() {
        return this.downloadStage == NEW;
    }

    public boolean unCompleted() {
        return this.downloadStage != COMPLETED && this.downloadStage != COMPLETED_IN_CACHE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TsDownload)) {
            return false;
        }
        TsDownload that = (TsDownload) o;
        return Objects.equals(uri, that.uri)
                && Objects.equals(sequence, that.sequence)
                && Objects.equals(m3u8Download, that.m3u8Download);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, sequence);
    }

    public static TsDownload getInstance(URI uri, Path filePath,
                                         Integer sequence, Path finalFilePath,
                                         Double durationInSeconds, M3u8Download m3u8Download, M3u8SecretKey m3u8SecretKey) {
        return new TsDownload(uri, filePath, sequence, finalFilePath, durationInSeconds, m3u8Download, m3u8SecretKey);
    }

    public enum TsDownloadStage {
        NEW, READING, FAILED, COMPLETED_IN_CACHE, COMPLETED,
    }

}
