package io.github.kanglong1023.m3u8.core;

import lombok.Getter;

@Getter
class M3u8DownloadOptions {

    private final boolean deleteTsOnComplete;

    private final boolean mergeWithoutConvertToMp4;

    private final OptionsForApplyTsCache optionsForApplyTsCache;

    private final M3u8HttpRequestConfigStrategy m3u8HttpRequestConfigStrategy;

    M3u8DownloadOptions(boolean deleteTsOnComplete,
                        boolean mergeWithoutConvertToMp4,
                        OptionsForApplyTsCache optionsForApplyTsCache,
                        M3u8HttpRequestConfigStrategy m3u8HttpRequestConfigStrategy) {
        this.deleteTsOnComplete = deleteTsOnComplete;
        this.optionsForApplyTsCache = optionsForApplyTsCache;
        this.mergeWithoutConvertToMp4 = mergeWithoutConvertToMp4;
        this.m3u8HttpRequestConfigStrategy = m3u8HttpRequestConfigStrategy;
    }

    public enum OptionsForApplyTsCache {

        START_OVER,

        SANITY_CHECK,

        FORCE_APPLY_CACHE_BASED_ON_FILENAME,
        ;

    }
}
