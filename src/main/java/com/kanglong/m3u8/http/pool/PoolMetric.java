package com.kanglong.m3u8.http.pool;

import com.kanglong.m3u8.support.log.WhiteboardMarkers;
import com.kanglong.m3u8.util.TextTableFormat;
import com.kanglong.m3u8.util.Utils;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Marker;

import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.kanglong.m3u8.util.Preconditions.checkNotNull;
import static com.kanglong.m3u8.util.TextTableFormat.textTableFormat;
import static com.kanglong.m3u8.util.Utils.mapToStrIfNull;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

@Slf4j
public class PoolMetric {

    private final PoolConfig poolConfig;

    private final ConcurrentMap<ScopedIdentity, LocalPoolMetric> localPoolMetrics = new ConcurrentHashMap<>();

    private final ConcurrentMap<ScopedIdentity, GlobalPoolMetric> globalPoolMetrics = new ConcurrentHashMap<>();

    private final ConcurrentMap<ScopedIdentity, CoteriePoolMetric> coteriePoolMetrics = new ConcurrentHashMap<>();

    public PoolMetric(PoolConfig poolConfig) {
        this.poolConfig = checkNotNull(poolConfig);
    }

    public void recordClaimFromLocalPool(ScopedIdentity identity, int execCount, int expect, int actual) {
        coteriePoolMetrics.computeIfAbsent(checkNotNull(identity), CoteriePoolMetric::new)
                .getClaimSlotsCount().getAndIncrement();
    }


    public void recordReleaseToLocalPool(ScopedIdentity identity, Integer totalSlots) {
        // alert one, after invoke recordClaimFromLocalPool
        coteriePoolMetrics.computeIfPresent(checkNotNull(identity), (k, v) -> v.setAlertTotalSlots(totalSlots));
    }

    public void recordClaimLink(ScopedIdentity identity) {
        localPoolMetrics.computeIfAbsent(checkNotNull(identity), LocalPoolMetric::new)
                .getClaimLinkCount().getAndIncrement();

    }

    public void recordReallocateBlock(ScopedIdentity identity) {
        localPoolMetrics.computeIfAbsent(checkNotNull(identity), LocalPoolMetric::new)
                .getReallocateBlockCount().getAndIncrement();
    }

    public void recordDestroyLocalPool(ScopedIdentity identity, Integer totalBlocks,
                                       Integer idleBlocks, Integer slotsOfIdleFirstBlock) {
        localPoolMetrics.computeIfAbsent(checkNotNull(identity), LocalPoolMetric::new)
                .setIdleBlocks(idleBlocks)
                .setTotalBlocks(totalBlocks)
                .setSlotsOfIdleFirstBlock(slotsOfIdleFirstBlock);
    }

    public void recordNewChunk(ScopedIdentity identity) {
        globalPoolMetrics.computeIfAbsent(checkNotNull(identity), GlobalPoolMetric::new)
                .getNewChunkCount().getAndIncrement();
    }

    public void recordDestroyGlobalPool(ScopedIdentity identity, Integer totalChunks,
                                        Integer idleChunks, Integer blocksOfIdleFirstChunk) {
        // actually, this metric is no synchronization required
        globalPoolMetrics.computeIfAbsent(checkNotNull(identity), GlobalPoolMetric::new)
                .setIdleChunks(idleChunks)
                .setTotalChunks(totalChunks)
                .setBlocksOfIdleFirstChunk(blocksOfIdleFirstChunk);
    }

    private String rate(long a, long b) {
        if (0 == a || 0 == b) {
            return "0.000%";
        } else {
            return Utils.rate(a, b) + "%";
        }
    }

    public void printMetrics() {

        int combinedTotalSlots = 0, combinedIdleSlots = 0;
        final int slotsPerBlock = poolConfig.slotsPerBlock();
        final int slotsPerChunk = poolConfig.slotsPerChunk();
        final int blocksPerChunk = poolConfig.blocksPerChunk();
        final Marker marker = WhiteboardMarkers.getWhiteboardMarker();
        final Comparator<ScopedIdentity> comparator = Comparator.nullsLast(Comparator.comparing(ScopedIdentity::getFullIdentity));

        final TextTableFormat globalPoolFormat = textTableFormat(log, marker)
                .setTitles("identity", "newChunkCount", "totalChunks", "idleChunks", "idleRate");
        List<Entry<ScopedIdentity, GlobalPoolMetric>> globalEntryList = globalPoolMetrics.entrySet().stream()
                .sorted(Entry.comparingByKey(comparator)).collect(Collectors.toList());
        for (Entry<ScopedIdentity, GlobalPoolMetric> entry : globalEntryList) {
            ScopedIdentity identity = entry.getKey();
            GlobalPoolMetric metric = entry.getValue();

            String idleChunks;
            String idleRate = null;
            Integer blocksOfIdleFirstChunk = metric.getBlocksOfIdleFirstChunk();
            String totalChunks = mapToStrIfNull(metric.getTotalChunks(), null);
            String newChunkCount = mapToStrIfNull(metric.getNewChunkCount(), null);

            if (null != blocksOfIdleFirstChunk && blocksOfIdleFirstChunk > 0 && blocksOfIdleFirstChunk < blocksPerChunk) {
                int fullIdleChunks = defaultIfNull(metric.getIdleChunks(), 0) - 1;
                idleChunks = (fullIdleChunks <= 0 ? "" : fullIdleChunks + "chunks + ") + blocksOfIdleFirstChunk + "blocks";
                if (Objects.nonNull(metric.getTotalChunks())) {
                    long totalBlocks = (long) metric.getTotalChunks() * blocksPerChunk;
                    long idleBlocks = blocksOfIdleFirstChunk + (long) blocksPerChunk *
                            (Objects.nonNull(metric.getIdleChunks()) ? metric.getIdleChunks() - 1 : 0);
                    idleRate = rate(idleBlocks, totalBlocks);

                    combinedIdleSlots += idleBlocks * slotsPerBlock;
                    combinedTotalSlots += totalBlocks * slotsPerBlock;
                }
            } else {
                idleChunks = mapToStrIfNull(metric.getIdleChunks(), null);
                if (Objects.nonNull(metric.getTotalChunks()) && Objects.nonNull(metric.getIdleChunks())) {
                    idleRate = rate(metric.getIdleChunks(), metric.getTotalChunks());

                    combinedIdleSlots += metric.getIdleChunks() * slotsPerChunk;
                    combinedTotalSlots += metric.getTotalChunks() * slotsPerChunk;
                }
            }
            globalPoolFormat.addData(identity.getFullIdentity(), newChunkCount, totalChunks, idleChunks, idleRate);
        }

        final TextTableFormat localPoolFormat = textTableFormat(log, marker)
                .setTitles("identity", "claimLinkCount", "reallocateBlockCount", "totalBlocks", "idleBlocks", "idleRate");
        List<Entry<ScopedIdentity, LocalPoolMetric>> localEntryList = localPoolMetrics.entrySet().stream()
                .sorted(Entry.comparingByKey(comparator)).collect(Collectors.toList());
        for (Entry<ScopedIdentity, LocalPoolMetric> entry : localEntryList) {
            ScopedIdentity identity = entry.getKey();
            LocalPoolMetric metric = entry.getValue();

            String idleBlocks;
            String idleRate = null;
            Integer slotsOfIdleFirstBlock = metric.getSlotsOfIdleFirstBlock();
            String totalBlocks = mapToStrIfNull(metric.getTotalBlocks(), null);
            String claimLinkCount = mapToStrIfNull(metric.getClaimLinkCount(), null);
            String reallocateBlockCount = mapToStrIfNull(metric.getReallocateBlockCount(), null);

            if (null != slotsOfIdleFirstBlock && slotsOfIdleFirstBlock > 0) {
                int fullIdleBlocks = defaultIfNull(metric.getIdleBlocks(), 0) - 1;
                idleBlocks = (fullIdleBlocks <= 0 ? "" : fullIdleBlocks + "blocks + ") + slotsOfIdleFirstBlock + "slots";
                if (Objects.nonNull(metric.getTotalBlocks())) {
                    long totalSlots = (long) metric.getTotalBlocks() * slotsPerBlock;
                    long idleSlots = slotsOfIdleFirstBlock + (long) slotsPerBlock *
                            (Objects.nonNull(metric.getIdleBlocks()) ? metric.getIdleBlocks() - 1 : 0);
                    idleRate = rate(idleSlots, totalSlots);

                    combinedIdleSlots += idleSlots;
                }
            } else {
                idleBlocks = mapToStrIfNull(metric.getIdleBlocks(), null);
                if (Objects.nonNull(metric.getTotalBlocks()) && Objects.nonNull(metric.getIdleBlocks())) {
                    idleRate = rate(metric.getIdleBlocks(), metric.getTotalBlocks());

                    combinedIdleSlots += metric.getIdleBlocks() * slotsPerBlock;
                }
            }
            localPoolFormat.addData(identity.getFullIdentity(), claimLinkCount, reallocateBlockCount, totalBlocks, idleBlocks, idleRate);
        }

        final TextTableFormat coteriePoolFormat = textTableFormat(log, marker)
                .setTitles("identity", "claimSlotsCount", "alertTotalSlots").setFullWidthGate(0);
        List<Entry<ScopedIdentity, CoteriePoolMetric>> coterieEntryList = coteriePoolMetrics.entrySet().stream()
                .sorted(Entry.comparingByKey(comparator)).collect(Collectors.toList());
        for (Entry<ScopedIdentity, CoteriePoolMetric> entry : coterieEntryList) {
            ScopedIdentity identity = entry.getKey();
            CoteriePoolMetric metric = entry.getValue();
            String claimSlotsCount = mapToStrIfNull(metric.getClaimSlotsCount(), null);
            String alertTotalSlots = mapToStrIfNull(metric.getAlertTotalSlots(), null);

            coteriePoolFormat.addData(identity.getFullIdentity(), claimSlotsCount, alertTotalSlots);
        }

        log.info(marker, "poolMetric report start: +=================================================+");

        log.info(marker, "\nglobalPoolMetric: ");
        globalPoolFormat.print();

        log.info(marker, "\nlocalPoolMetric: ");
        localPoolFormat.print();

        log.info(marker, "\ncoteriePoolMetric: ");
        coteriePoolFormat.print();

        log.info(marker, "\nstrict slot usage: combinedTotalSlots={}, combinedIdleSlots={}, combinedIdleRate={}",
                combinedTotalSlots, combinedIdleSlots, rate(combinedIdleSlots, combinedTotalSlots));

        log.info(marker, "poolMetric report end: +===================================================+");
    }


    @Getter
    @Setter
    @Accessors(chain = true)
    private static class GlobalPoolMetric {

        private Integer idleChunks;

        private Integer totalChunks;

        private final ScopedIdentity identity;

        private Integer blocksOfIdleFirstChunk;

        private final AtomicLong newChunkCount = new AtomicLong();

        private GlobalPoolMetric(ScopedIdentity identity) {
            this.identity = checkNotNull(identity);
        }
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    private static class LocalPoolMetric {

        private Integer idleBlocks;

        private Integer totalBlocks;

        private final ScopedIdentity identity;

        private Integer slotsOfIdleFirstBlock;

        private final AtomicLong claimLinkCount = new AtomicLong();

        private final AtomicLong reallocateBlockCount = new AtomicLong();

        private LocalPoolMetric(ScopedIdentity identity) {
            this.identity = checkNotNull(identity);
        }
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    private static class CoteriePoolMetric {

        private Integer alertTotalSlots;

        private final ScopedIdentity identity;

        private final AtomicLong claimSlotsCount = new AtomicLong();

        private CoteriePoolMetric(ScopedIdentity identity) {
            this.identity = checkNotNull(identity);
        }
    }

}
