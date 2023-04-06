package com.kanglong.m3u8.http.pool;

import com.kanglong.m3u8.util.Preconditions;

import java.util.function.IntUnaryOperator;

public class PoolConfig {

    public static final PoolConfig DEFAULT = custom().build();

    private final int slotsPerLink;

    private final int slotsPerBlock;

    private final int blocksPerChunk;

    private final boolean printMetric;

    private final int globalPoolCount;

    private final int blocksPerReallocate;

    private final int slotsOfInitialCoterie;

    private final int blocksOfInitialLocalPool;

    private final int chunksOfInitialGlobalPool;

    private final IntUnaryOperator slotsClaimPlannerOfCoterie;

    private PoolConfig(int slotsPerLink,
                       int slotsPerBlock,
                       int blocksPerChunk,
                       boolean printMetric,
                       int globalPoolCount,
                       int blocksPerReallocate,
                       int slotsOfInitialCoterie,
                       int blocksOfInitialLocalPool,
                       int chunksOfInitialGlobalPool,
                       IntUnaryOperator slotsClaimPlannerOfCoterie) {
        this.printMetric = printMetric;
        this.slotsPerLink = slotsPerLink;
        this.slotsPerBlock = slotsPerBlock;
        this.blocksPerChunk = blocksPerChunk;
        this.globalPoolCount = globalPoolCount;
        this.blocksPerReallocate = blocksPerReallocate;
        this.slotsOfInitialCoterie = slotsOfInitialCoterie;
        this.blocksOfInitialLocalPool = blocksOfInitialLocalPool;
        this.chunksOfInitialGlobalPool = chunksOfInitialGlobalPool;
        this.slotsClaimPlannerOfCoterie = slotsClaimPlannerOfCoterie;
    }

    public int slotsPerLink() {
        return this.slotsPerLink;
    }

    public int slotsPerBlock() {
        return this.slotsPerBlock;
    }

    public int blocksPerChunk() {
        return this.blocksPerChunk;
    }

    public boolean ifPrintMetric() {
        return printMetric;
    }

    public int globalPoolCount() {
        return this.globalPoolCount;
    }

    public int blocksPerReallocate() {
        return this.blocksPerReallocate;
    }

    public int slotsOfInitialCoterie() {
        return this.slotsOfInitialCoterie;
    }

    public int blocksOfInitialLocalPool() {
        return this.blocksOfInitialLocalPool;
    }

    public int chunksOfInitialGlobalPool() {
        return this.chunksOfInitialGlobalPool;
    }

    public IntUnaryOperator slotsClaimPlannerOfCoterie() {
        return this.slotsClaimPlannerOfCoterie;
    }

    public int slotsPerChunk() {
        return this.slotsPerBlock * this.blocksPerChunk;
    }

    public int atLeastAllocatedSlots(int atLeastCount) {
        int slotsPerChunk = slotsPerChunk();
        int globalPoolCount = this.globalPoolCount;
        int chunksOfInitialGlobalPool = this.chunksOfInitialGlobalPool;

        int atLeastAllocatedSlots;
        if (chunksOfInitialGlobalPool > 0) {
            atLeastAllocatedSlots = globalPoolCount * chunksOfInitialGlobalPool * slotsPerChunk;
        } else {
            atLeastAllocatedSlots = globalPoolCount * slotsPerChunk;
        }

        if (atLeastAllocatedSlots >= atLeastCount) {
            return atLeastAllocatedSlots;
        }

        return (int) (atLeastAllocatedSlots + Math.ceil(((atLeastCount - atLeastAllocatedSlots) * 1.0) / slotsPerChunk) * slotsPerChunk);
    }

    @Override
    public String toString() {
        return "PoolConfig{" +
                "slotsPerLink=" + slotsPerLink +
                ", slotsPerBlock=" + slotsPerBlock +
                ", blocksPerChunk=" + blocksPerChunk +
                ", printMetric=" + printMetric +
                ", globalPoolCount=" + globalPoolCount +
                ", blocksPerReallocate=" + blocksPerReallocate +
                ", slotsOfInitialCoterie=" + slotsOfInitialCoterie +
                ", blocksOfInitialLocalPool=" + blocksOfInitialLocalPool +
                ", chunksOfInitialGlobalPool=" + chunksOfInitialGlobalPool +
                ", slotsClaimPlannerOfCoterie=" + slotsClaimPlannerOfCoterie +
                '}';
    }

    public static Builder custom() {
        return new Builder();
    }

    public static Builder copy(final PoolConfig config) {
        return new Builder()
                .slotsPerLink(config.slotsPerLink())
                .printMetric(config.ifPrintMetric())
                .slotsPerBlock(config.slotsPerBlock())
                .blocksPerChunk(config.blocksPerChunk())
                .globalPoolCount(config.globalPoolCount())
                .blocksPerReallocate(config.blocksPerReallocate())
                .slotsOfInitialCoterie(config.slotsOfInitialCoterie())
                .blocksOfInitialLocalPool(config.blocksOfInitialLocalPool())
                .chunksOfInitialGlobalPool(config.chunksOfInitialGlobalPool())
                .slotsClaimPlannerOfCoterie(config.slotsClaimPlannerOfCoterie());
    }

    public static class Builder {

        private int slotsPerLink;

        private int slotsPerBlock;

        private int blocksPerChunk;

        private boolean printMetric;

        private int globalPoolCount;

        private int blocksPerReallocate;

        private int slotsOfInitialCoterie;

        private int blocksOfInitialLocalPool;

        private int chunksOfInitialGlobalPool;

        private IntUnaryOperator slotsClaimPlannerOfCoterie;

        public Builder() {
            super();

            this.slotsPerLink = 8;
            this.slotsPerBlock = 8;
            this.blocksPerChunk = 8;
            this.printMetric = false;
            this.globalPoolCount = 1;
            this.blocksPerReallocate = 1;
            this.slotsOfInitialCoterie = 2;
            this.blocksOfInitialLocalPool = 1;
            this.chunksOfInitialGlobalPool = 0;
            this.slotsClaimPlannerOfCoterie = execCount -> 2;
        }

        /**
         * Suggestion: slotsPerLink should be a little smaller or a little bigger than slotsPerBlock
         */
        public Builder slotsPerLink(final int slotsPerLink) {
            Preconditions.checkPositive(slotsPerLink, "slotsPerLink");
            this.slotsPerLink = slotsPerLink;
            return this;
        }

        public Builder slotsPerBlock(final int slotsPerBlock) {
            Preconditions.checkPositive(slotsPerBlock, "slotsPerBlock");
            this.slotsPerBlock = slotsPerBlock;
            return this;
        }

        public Builder blocksPerChunk(final int blocksPerChunk) {
            Preconditions.checkPositive(blocksPerChunk, "blocksPerChunk");
            this.blocksPerChunk = blocksPerChunk;
            return this;
        }

        public Builder printMetric() {
            this.printMetric = true;
            return this;
        }

        public Builder printMetric(final boolean printMetric) {
            this.printMetric = printMetric;
            return this;
        }

        public Builder globalPoolCount(final int globalPoolCount) {
            Preconditions.checkPositive(globalPoolCount, "globalPoolCount");
            Preconditions.checkArgument((globalPoolCount & -globalPoolCount) == globalPoolCount,
                    "globalPoolCount is not a power of 2: %d", globalPoolCount);
            this.globalPoolCount = globalPoolCount;
            return this;
        }

        public Builder blocksPerReallocate(final int blocksPerReallocate) {
            Preconditions.checkPositive(blocksPerReallocate, "blocksPerReallocate");
            this.blocksPerReallocate = blocksPerReallocate;
            return this;
        }

        public Builder slotsOfInitialCoterie(final int slotsOfInitialCoterie) {
            Preconditions.checkNonNegative(slotsOfInitialCoterie, "slotsOfInitialCoterie");
            this.slotsOfInitialCoterie = slotsOfInitialCoterie;
            return this;
        }

        public Builder blocksOfInitialLocalPool(final int blocksOfInitialLocalPool) {
            Preconditions.checkNonNegative(blocksOfInitialLocalPool, "blocksOfInitialLocalPool");
            this.blocksOfInitialLocalPool = blocksOfInitialLocalPool;
            return this;
        }

        public Builder chunksOfInitialGlobalPool(final int chunksOfInitialGlobalPool) {
            Preconditions.checkNonNegative(chunksOfInitialGlobalPool, "chunksOfInitialGlobalPool");
            this.chunksOfInitialGlobalPool = chunksOfInitialGlobalPool;
            return this;
        }

        /**
         * slotsClaimPlannerOfCoterie's function value must be <= slotsPerLink and <= slotsPerBlock
         */
        public Builder slotsClaimPlannerOfCoterie(IntUnaryOperator slotsClaimPlannerOfCoterie) {
            Preconditions.checkNotNull(slotsClaimPlannerOfCoterie, "slotsClaimPlannerOfCoterie");
            this.slotsClaimPlannerOfCoterie = slotsClaimPlannerOfCoterie;
            return this;
        }

        public PoolConfig build() {
            int slotsPerLink = this.slotsPerLink;
            int slotsPerBlock = this.slotsPerBlock;
            int blocksPerChunk = this.blocksPerChunk;
            int blocksPerReallocate = this.blocksPerReallocate;
            int slotsOfInitialCoterie = this.slotsOfInitialCoterie;
            int blocksOfInitialLocalPool = this.blocksOfInitialLocalPool;

            // slotsClaimPlannerOfCoterie's function value also should be <= slotsPerLink and <= slotsPerBlock
            Preconditions.checkArgument(slotsOfInitialCoterie <= slotsPerLink && slotsOfInitialCoterie <= slotsPerBlock,
                    "slotsOfInitialCoterie illegal, must be <= slotsPerLink and <= slotsPerBlock");

            Preconditions.checkArgument(blocksPerReallocate <= blocksPerChunk,
                    "blocksPerReallocate illegal, must be <= blocksPerChunk");

            Preconditions.checkArgument(blocksOfInitialLocalPool <= blocksPerChunk,
                    "blocksOfInitialLocalPool illegal, must be <= blocksPerChunk");

            return new PoolConfig(
                    this.slotsPerLink,
                    this.slotsPerBlock,
                    this.blocksPerChunk,
                    this.printMetric,
                    this.globalPoolCount,
                    this.blocksPerReallocate,
                    this.slotsOfInitialCoterie,
                    this.blocksOfInitialLocalPool,
                    this.chunksOfInitialGlobalPool,
                    this.slotsClaimPlannerOfCoterie);
        }
    }

}
