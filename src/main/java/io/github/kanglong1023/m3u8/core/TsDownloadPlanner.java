package io.github.kanglong1023.m3u8.core;

import io.github.kanglong1023.m3u8.core.M3u8DownloadOptions.OptionsForApplyTsCache;
import io.github.kanglong1023.m3u8.core.M3u8Resolver.MediaSegment;
import io.github.kanglong1023.m3u8.http.config.HttpRequestConfig;
import io.github.kanglong1023.m3u8.util.CollUtil;
import io.github.kanglong1023.m3u8.util.Preconditions;
import io.github.kanglong1023.m3u8.util.Utils;
import io.github.kanglong1023.m3u8.util.function.Try;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.kanglong1023.m3u8.core.M3u8Download.m3u8StoreName;
import static io.github.kanglong1023.m3u8.core.M3u8Download.unFinishedTsExtension;
import static io.github.kanglong1023.m3u8.util.Preconditions.*;

@Slf4j
public class TsDownloadPlanner {

    private final M3u8Download m3u8Download;

    private final BiFunction<URI, HttpRequestConfig, ByteBuffer> bytesResponseGetter;

    public TsDownloadPlanner(M3u8Download m3u8Download,
                             BiFunction<URI, HttpRequestConfig, ByteBuffer> bytesResponseGetter) {
        this.m3u8Download = checkNotNull(m3u8Download);
        this.bytesResponseGetter = checkNotNull(bytesResponseGetter);
    }

    public List<TsDownload> plan() {
        M3u8Download m3u8Download = this.m3u8Download;
        BiFunction<URI, HttpRequestConfig, ByteBuffer> bytesResponseGetter = this.bytesResponseGetter;

        URI m3u8Uri = m3u8Download.getUri();
        Path tsDir = m3u8Download.getTsDir();
        String identity = m3u8Download.getIdentity();
        M3u8DownloadOptions m3u8DownloadOptions = m3u8Download.getM3u8DownloadOptions();

        Path m3u8StorePath = tsDir.resolve(m3u8StoreName);
        OptionsForApplyTsCache optionsForApplyTsCache = m3u8DownloadOptions.getOptionsForApplyTsCache();
        M3u8HttpRequestConfigStrategy requestConfigStrategy = m3u8DownloadOptions.getM3u8HttpRequestConfigStrategy();

        // resolve
        M3u8Resolver m3u8Resolver = new M3u8Resolver(m3u8Uri, requestConfigStrategy, bytesResponseGetter);
        m3u8Resolver.resolve();

        // check mediaSegments
        List<MediaSegment> mediaSegments = m3u8Resolver.getMediaSegments();
        m3u8Check(CollectionUtils.isNotEmpty(mediaSegments), "empty mediaSegments: %s", identity);

        // checkCache
        checkTsCache(identity, tsDir, m3u8StorePath, optionsForApplyTsCache, m3u8Resolver);

        // fetchSecretKey
        Map<MediaSegment, M3u8SecretKey> secretKeyMap = m3u8Resolver.fetchSecretKey(mediaSegments);

        // convert
        List<TsDownload> tsDownloads = convertToTsDownloads(OptionsForApplyTsCache.START_OVER == optionsForApplyTsCache, tsDir, mediaSegments, secretKeyMap);

        // m3u8Store
        genM3u8Store(m3u8Resolver, m3u8StorePath);

        return tsDownloads;
    }

    private void checkTsCache(String identity, Path tsDir, Path m3u8StorePath,
                              OptionsForApplyTsCache optionsForApplyTsCache, M3u8Resolver m3u8Resolver) {
        Preconditions.checkArgument(ObjectUtils.allNotNull(identity, tsDir, m3u8StorePath));
        Preconditions.checkArgument(ObjectUtils.allNotNull(optionsForApplyTsCache, m3u8StorePath));

        if (OptionsForApplyTsCache.START_OVER == optionsForApplyTsCache) {
            log.info("start download all over: {}", identity);
            return;
        }

        if (OptionsForApplyTsCache.FORCE_APPLY_CACHE_BASED_ON_FILENAME == optionsForApplyTsCache) {
            log.info("start download force apply cache based on filename: {}", identity);
            return;
        }

        if (OptionsForApplyTsCache.SANITY_CHECK != optionsForApplyTsCache) {
            return;
        }

        Collection<Path> possibleCompletedFiles;
        // except dir, m3u8StoreFile, unFinishedTs and hidden files
        List<Path> ignoredPaths = Arrays.asList(tsDir, m3u8StorePath);
        BiPredicate<Path, BasicFileAttributes> matcher = (p, attr) -> Try.of(() -> !Files.isHidden(p)
                && !ignoredPaths.contains(p)
                && !p.getFileName().toString().endsWith("." + unFinishedTsExtension)).get();

        try (Stream<Path> pathStream = Try.of(() -> Files.find(tsDir, 2, matcher)).get()) {
            possibleCompletedFiles = pathStream.limit(10).collect(Collectors.toList());
        }

        if (CollectionUtils.isEmpty(possibleCompletedFiles)) {
            return;
        }

        String suggestion = "You can adopt the following suggestion:\n" +
                "\t1. use startOver, it would be ignore and delete possible completed files;\n" +
                "\t2. modify fileName, it would be keep away from previously downloaded m3u8.\n";
        if (Files.notExists(m3u8StorePath)) {
            log.error("found possible completed files, but m3u8StoreFile({}) is not exists, " +
                    "it cannot be confirmed whether it belongs to the current m3u8: {}\n{}", m3u8StorePath, identity, suggestion);
            m3u8Exception("m3u8StoreFile not exists");
        }

        if (!Files.isRegularFile(m3u8StorePath)) {
            log.error("found possible completed files, but m3u8StoreFile({}) is not a regular file, " +
                    "it cannot be confirmed whether it belongs to the current m3u8: {}\n{}", m3u8StorePath, identity, suggestion);
            m3u8Exception("m3u8StoreFile is not a regular File");
        }

        M3u8Store m3u8Store = null;
        try {
            m3u8Store = M3u8Store.load(m3u8StorePath);
        } catch (Exception ex) {
            log.error("found possible completed files, but load m3u8Store({}) error, " +
                    "it cannot be confirmed whether it belongs to the current m3u8: {}\n{}", m3u8StorePath, identity, suggestion);
            m3u8Exception("load m3u8Store error", ex);
        }

        if (Objects.isNull(m3u8Store.getM3u8Uri()) || Objects.isNull(m3u8Store.getFinalM3u8Uri())) {
            log.error("found possible completed files, but m3u8Store({}) is invalid, " +
                    "it cannot be confirmed whether it belongs to the current m3u8: {}\n{}", m3u8StorePath, identity, suggestion);
            m3u8Exception("m3u8Store is invalid");
        }

        if (Objects.equals(m3u8Resolver.getM3u8Uri(), m3u8Store.getM3u8Uri())
                && Objects.equals(m3u8Resolver.getFinalM3u8Uri(), m3u8Store.getFinalM3u8Uri())
                && Objects.equals(m3u8Resolver.getMasterM3u8Uri(), m3u8Store.getMasterM3u8Uri())) {
            log.info("found possible completed files, found m3u8Store: {}\n{}", identity, m3u8Store.toPlainString());
        } else {
            suggestion = "You can adopt the following suggestion:\n" +
                    "\t1. use startOver, it would be ignore and delete possible completed files;\n" +
                    "\t2. modify fileName, it would be keep away from previously downloaded m3u8;\n" +
                    "\t3. consider use forceCacheAssignmentBasedOnFileName, you can read the details of m3u8Store({}) " +
                    "to determine whether to use the cache of {}";
            log.error("found possible completed files, but it maybe belongs to another m3u8: {}\n" + suggestion,
                    identity, m3u8StorePath, tsDir);
            m3u8Exception("possible completed files belongs to another m3u8");
        }
    }

    private List<TsDownload> convertToTsDownloads(boolean ignoreCache, Path tsDir,
                                                  List<MediaSegment> mediaSegments,
                                                  Map<MediaSegment, M3u8SecretKey> secretKeyMap) {
        Preconditions.checkArgument(ObjectUtils.allNotNull(tsDir, mediaSegments, secretKeyMap));

        List<TsDownload> tsDownloads = CollUtil.newArrayListWithCapacity(mediaSegments.size());
        for (MediaSegment mediaSegment : mediaSegments) {
            URI tsUri = mediaSegment.getUri();
            String tsFileName = Paths.get(tsUri.getPath()).getFileName().toString();
            Path tsFile = tsDir.resolve(tsFileName + "." + unFinishedTsExtension);

            if (Utils.isFileNameTooLong(tsFile.toString())) {
                String md5 = Utils.md5(tsUri.toString());
                Path rtsFile = tsDir.resolve(md5 + "." + unFinishedTsExtension);
                log.info("fileName too long, use {}: {}", rtsFile, tsFile);
                tsFile = rtsFile;
                tsFileName = md5 + ".ts";
            }

            if (Files.exists(tsFile)) {
                Path aFinalTsFile = tsFile;
                Preconditions.checkArgument(Try.run(() -> Files.delete(aFinalTsFile)).isSuccess(), "delete file error: %s", tsFile);
                if (log.isDebugEnabled()) {
                    log.debug("delete exists file: {}", tsFile);
                }
            }

            boolean completed = false;
            Path finalTsFile = tsDir.resolve(tsFileName);
            if (Files.exists(finalTsFile)) {
                if (ignoreCache || Try.of(() -> Files.size(finalTsFile)).get() <= 0) {
                    Preconditions.checkArgument(Try.run(() -> Files.delete(finalTsFile)).isSuccess(), "delete file error: %s", finalTsFile);
                } else {
                    completed = true;
                    if (log.isDebugEnabled()) {
                        log.debug("uri={} complete, use cache: {}", tsUri, finalTsFile);
                    }
                }
            }

            M3u8SecretKey m3u8SecretKey = secretKeyMap.get(mediaSegment);
            TsDownload tsDownload = TsDownload.getInstance(tsUri, tsFile,
                    mediaSegment.getSequence(), finalTsFile, mediaSegment.getDurationInSeconds(), m3u8Download, m3u8SecretKey);

            if (completed) {
                tsDownload.completeInCache();
            }
            tsDownloads.add(tsDownload);
        }

        return tsDownloads;
    }

    private M3u8Store genM3u8Store(M3u8Resolver m3u8Resolver, Path m3u8StorePath) {
        Preconditions.checkNotNull(m3u8Resolver);
        Preconditions.checkNotNull(m3u8StorePath);

        M3u8Store m3u8Store = new M3u8Store(m3u8Resolver.getM3u8Uri(),
                m3u8Resolver.getFinalM3u8Uri(), m3u8Resolver.getMasterM3u8Uri(),
                m3u8Resolver.getFinalM3u8Content(), m3u8Resolver.getMasterM3u8Content());
        try {

            Files.deleteIfExists(m3u8StorePath);

            m3u8Store.store(m3u8StorePath);
        } catch (Exception ex) {
            log.error("save m3u8Store({}) error: {}", m3u8StorePath, ex.getMessage());
            throw new RuntimeException(ex);
        }
        return m3u8Store;
    }

}
