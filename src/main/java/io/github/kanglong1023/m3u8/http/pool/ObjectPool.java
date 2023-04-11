package io.github.kanglong1023.m3u8.http.pool;

import io.github.kanglong1023.m3u8.util.Preconditions;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

public class ObjectPool<T> {

    private final PoolConfig poolConfig;

    private final PoolMetric poolMetric;

    private final GlobalPool<T>[] globalPools;

    private final PooledObjFactory<T> pooledObjFactory;

    private final AtomicInteger lastIdx = new AtomicInteger(-1);

    private final ThreadLocal<LocalPool<T>> localPoolTLS = new ThreadLocal<>();

    public ObjectPool(String poolIdentity, PoolConfig poolConfig, PoolMetric poolMetric, PooledObjFactory<T> pooledObjFactory) {
        Objects.requireNonNull(poolIdentity);
        this.poolConfig = Objects.requireNonNull(poolConfig);
        this.poolMetric = Objects.requireNonNull(poolMetric);
        this.pooledObjFactory = Objects.requireNonNull(pooledObjFactory);

        Class<T> type = pooledObjFactory.getType();
        ScopedIdentity objectPoolIdentity = new ScopedIdentity(poolIdentity);
        final int globalPoolCount = poolConfig.globalPoolCount();
        Preconditions.checkArgument((globalPoolCount & -globalPoolCount) == globalPoolCount,
                "globalPoolCount is not a power of 2: %d", globalPoolCount);

        @SuppressWarnings("unchecked")
        GlobalPool<T>[] pools = (GlobalPool<T>[]) Array.newInstance(GlobalPool.class, globalPoolCount);
        for (int i = 0; i < globalPoolCount; i++) {
            String identity = type.getSimpleName() + "-GlobalPool-" + i;
            ScopedIdentity scopedIdentity = new ScopedIdentity(identity, objectPoolIdentity);
            pools[i] = new GlobalPool<>(scopedIdentity, pooledObjFactory, poolConfig, this.poolMetric);
        }
        this.globalPools = pools;
    }

    private LocalPool<T> localPoolInitial() {
        int slotsPerLink = poolConfig.slotsPerLink();
        GlobalPool<T> globalPool = selectGlobalPool();
        int blocksPerReallocate = poolConfig.blocksPerReallocate();
        int slotsOfInitialCoterie = poolConfig.slotsOfInitialCoterie();
        String identity = Thread.currentThread().getName() + "-LocalPool";

        // lock op
        List<Block<T>> blocks = globalPool.allocateBlock(poolConfig.blocksOfInitialLocalPool());

        return new LocalPool<>(new ScopedIdentity(identity, globalPool.getScopedIdentity()), globalPool,
                blocksPerReallocate, slotsPerLink, slotsOfInitialCoterie, pooledObjFactory, blocks, poolMetric);
    }

    private GlobalPool<T> selectGlobalPool() {
        // round-robin
        int cur = lastIdx.incrementAndGet();
        return globalPools[cur & (globalPools.length - 1)];
    }

    public CoteriePool<T> allocateCoterie(ScopedIdentity identity) {
        LocalPool<T> localPool = getLocalPool();

        IntUnaryOperator claimPlannerOfCoterie = poolConfig.slotsClaimPlannerOfCoterie();
        return localPool.allocateCoterie(identity, claimPlannerOfCoterie);
    }

    public LocalPool<T> getLocalPool() {
        LocalPool<T> localPool = localPoolTLS.get();
        if (null == localPool) {
            localPool = localPoolInitial();
            localPoolTLS.set(localPool);
        }
        return localPool;
    }

    public void destroyLocalPool() {
        LocalPool<T> localPool = localPoolTLS.get();
        if (null != localPool) {
            try {
                localPool.destroy();
            } finally {
                localPoolTLS.remove();
            }
        }
    }


    public void destroy() {
        for (GlobalPool<T> globalPool : this.globalPools) {
            globalPool.destroy();
        }
    }

    public void printMetrics() {
        poolMetric.printMetrics();
    }

}
