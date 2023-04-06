package com.kanglong.m3u8.core;

import com.kanglong.m3u8.http.config.HttpRequestConfig;
import com.kanglong.m3u8.util.CollUtil;
import com.kanglong.m3u8.util.Utils;
import com.kanglong.m3u8.util.VideoUtil;
import com.kanglong.m3u8.util.function.Try;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static com.kanglong.m3u8.util.Preconditions.*;
import static com.kanglong.m3u8.util.Utils.checkAndCreateDir;
import static java.lang.String.format;

@Slf4j
public class M3u8Download {

    public static final String m3u8StoreName = "m3u8Index.xml";

    public static final String unFinishedTsExtension = "progress";

    private final URI uri;

    private final Path tsDir;

    private final String fileName;

    private final String identity;

    private final Path targetFileDir;

    private final M3u8DownloadOptions m3u8DownloadOptions;

    private final LongAdder downloadBytes = new LongAdder();

    private final LongAdder failedTsDownloads = new LongAdder();

    private final LongAdder finishedTsDownloads = new LongAdder();

    private final List<TsDownload> tsDownloads = CollUtil.newArrayList();

    private final M3u8DownloadListener.M3u8DownloadListeners downloadListener;

    private final Set<TsDownload> readingTsDownloads = CollUtil.newConcurrentHashSet();

    public M3u8Download(URI uri, String fileName,
                        Path workHome, Path targetFileDir,
                        List<M3u8DownloadListener> listeners,
                        M3u8DownloadOptions m3u8DownloadOptions) {
        try {

            m3u8Check(Objects.nonNull(uri), "uri is null");
            m3u8Check(Utils.isValidURL(uri), "uri is invalid: %s", uri);
            this.uri = uri;

            checkAndCreateDir(targetFileDir, "targetFileDir");
            this.targetFileDir = targetFileDir;

            String finalFileName = getFinalFileName(fileName);
            Path file = targetFileDir.resolve(finalFileName);
            m3u8Check(Files.notExists(file), "file is exists：%s", file);
            m3u8Check(!Utils.isFileNameTooLong(file.toString()), "fileName too long: %s", file);
            this.fileName = finalFileName;

            if (null == workHome) {
                workHome = targetFileDir;
            } else {
                checkAndCreateDir(workHome, "workHome");
            }
            Path tsSavePath = checkAndCreateDir(workHome.resolve(Utils.mainName(this.fileName)), "tsDir");
            Path tsFileTest = tsSavePath.resolve("1234567890-1234567890-1234567890" + "." + unFinishedTsExtension);
            m3u8Check(!Utils.isFileNameTooLong(tsFileTest.toString()), "tsDir too long: %s", tsSavePath);
            this.tsDir = tsSavePath;

            this.identity = this.fileName + "@" + this.hashCode();
            this.m3u8DownloadOptions = checkNotNull(m3u8DownloadOptions);
            this.downloadListener = new M3u8DownloadListener.M3u8DownloadListeners(this, listeners);

            log.info("download m3u8, uri={}, fileName={}, targetFileDir={}, identity={}",
                    this.uri, this.fileName, this.targetFileDir, this.identity);

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static M3u8DownloadBuilder builder() {
        return new M3u8DownloadBuilder();
    }

    public static String getFinalFileName(String fileName) {
        m3u8Check(StringUtils.isNotBlank(fileName), "fileName is blank");
        String mainName = Utils.mainName(fileName);
        m3u8Check(StringUtils.isNotBlank(mainName), "fileName is invalid: %s", fileName);
        return mainName + ".mp4";
    }

    public List<TsDownload> resolveTsDownloads(BiFunction<URI, HttpRequestConfig, ByteBuffer> bytesResponseGetter) {
        TsDownloadPlanner tsDownloadPlanner = new TsDownloadPlanner(this, bytesResponseGetter);

        List<TsDownload> downloads = tsDownloadPlanner.plan();
        List<TsDownload> newDownloads = downloads.stream().filter(TsDownload::isNew).collect(Collectors.toList());

        this.tsDownloads.clear();
        this.tsDownloads.addAll(downloads);

        log.info("resolved {} ts downloads, {} need to download: {}", downloads.size(), newDownloads.size(), this.identity);

        return newDownloads;
    }

    public void notifyDownloadStart() {
        this.downloadBytes.reset();
        this.failedTsDownloads.reset();
        this.readingTsDownloads.clear();
        this.finishedTsDownloads.reset();

        this.downloadListener.start(this);
    }

    public void mergeIntoVideo() {
        String identity = this.identity;
        List<TsDownload> downloadList = this.tsDownloads;

        // check uncompletedTs
        List<String> uncompletedTs = downloadList.stream().filter(TsDownload::unCompleted)
                .map(d -> d.getFinalFilePath().getFileName().toString()).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(uncompletedTs)) {
            throw new IllegalStateException(format("ts unCompleted, identity=%s: %s", identity, uncompletedTs));
        }

        // cal fileSize
        long totalSizeOfAllTsFiles = downloadList.stream().mapToLong(d -> Try.ofCallable(() -> checkPositive(Files.size(d.getFinalFilePath()),
                format("file(%s) size", d.getFinalFilePath()))).get()).sum();
        String totalSize = Utils.bytesFormat(totalSizeOfAllTsFiles, 3);

        // cal duration
        BigDecimal sumOfDuration = downloadList.stream().map(d -> BigDecimal.valueOf(d.getDurationInSeconds()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal seconds = BigDecimal.valueOf(sumOfDuration.toBigInteger().longValue());
        BigDecimal nanos = sumOfDuration.subtract(seconds).multiply(BigDecimal.TEN.pow(9), new MathContext(0, RoundingMode.DOWN));
        Duration duration = Duration.ofSeconds(seconds.longValue(), nanos.longValue());

        log.info("download finished, {} ts, totalSizeOfAllTsFiles={}, duration={}, identity={}",
                downloadList.size(), totalSize, Utils.secondsFormat(duration.getSeconds()), identity);

        Path targetFile = targetFileDir.resolve(fileName);
        m3u8Check(Files.notExists(targetFile), "targetFile is exists：%s", targetFile);

        List<Path> tsFiles = downloadList.stream()
                .sorted(Comparator.comparing(TsDownload::getSequence))
                .map(TsDownload::getFinalFilePath).collect(Collectors.toList());

        // merge
        if (this.m3u8DownloadOptions.isMergeWithoutConvertToMp4()) {
            // merge into large ts
            try (FileChannel fileChannel = FileChannel.open(targetFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                for (Path ts : tsFiles) {
                    try (FileChannel inputStreamChannel = FileChannel.open(ts, StandardOpenOption.READ)) {
                        inputStreamChannel.transferTo(0, inputStreamChannel.size(), fileChannel);
                    }
                }
            } catch (Exception e) {
                log.error("merge ts error(file={" + targetFile + "}): " + e.getMessage(), e);
                throw new RuntimeException(e);
            }
        } else {
            // merge into mp4
            m3u8Check(VideoUtil.convertToMp4(targetFile, tsFiles), "merge failed");
        }

        // delete ts
        if (this.m3u8DownloadOptions.isDeleteTsOnComplete()) {
            try {
                Utils.deleteRecursively(this.tsDir);
            } catch (IOException e) {
                log.error("delete tsDir(" + tsDir + ") error" + e.getMessage(), e);
            }
        }

        downloadListener.end(this);

        log.info("merge complete path={}", targetFile);
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    /**
     * Strict hashcode should be used for this class
     */
    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    public URI getUri() {
        return this.uri;
    }

    public Path getTsDir() {
        return this.tsDir;
    }

    public String getFileName() {
        return this.fileName;
    }

    public String getIdentity() {
        return this.identity;
    }

    public M3u8DownloadOptions getM3u8DownloadOptions() {
        return this.m3u8DownloadOptions;
    }

    public void startReadTs(TsDownload tsDownload) {
        this.readingTsDownloads.add(tsDownload);
    }

    public void downloadBytes(int bytes) {
        this.downloadBytes.add(bytes);
    }

    public int getTsDownloadsCount() {
        return this.tsDownloads.size();
    }

    public long getDownloadBytes() {
        return this.downloadBytes.sum();
    }

    public long getFinishedTsDownloads() {
        return this.finishedTsDownloads.sum();
    }

    public long getFailedTsDownloads() {
        return this.failedTsDownloads.sum();
    }

    public Set<TsDownload> getReadingTsDownloads() {
        return Collections.unmodifiableSet(this.readingTsDownloads);
    }

    public void OnFinishTsDownload(TsDownload tsDownload, boolean failed) {
        this.readingTsDownloads.remove(tsDownload);
        if (failed) {
            this.failedTsDownloads.increment();
        } else {
            this.finishedTsDownloads.increment();
        }
    }

    public long getInstantReadingAndRemainedCount() {
        return getTsDownloadsCount() - getFinishedTsDownloads() - getFailedTsDownloads();
    }
}
