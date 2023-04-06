package com.kanglong.m3u8.http.pool;

import com.kanglong.m3u8.util.CollUtil;
import com.kanglong.m3u8.util.Preconditions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.IntUnaryOperator;

@Slf4j
public class CoteriePool<T> {

    private int claimCount = 0;

    private final Recycler<T> recycler;

    private final PoolMetric poolMetric;

    private final LocalPool<T> localPool;

    private final ScopedIdentity identity;

    private final IntUnaryOperator claimPlaner;

    private final PooledObjFactory<T> pooledObjFactory;

    private final ConcurrentLinkedDeque<Slot<T>> freeList;

    public CoteriePool(ScopedIdentity identity, LocalPool<T> localPool, IntUnaryOperator claimPlaner,
                       PooledObjFactory<T> pooledObjFactory, Collection<Slot<T>> slots, PoolMetric poolMetric) {
        this.identity = Objects.requireNonNull(identity);
        this.localPool = Objects.requireNonNull(localPool);
        this.poolMetric = Objects.requireNonNull(poolMetric);
        this.claimPlaner = Objects.requireNonNull(claimPlaner);
        this.pooledObjFactory = Objects.requireNonNull(pooledObjFactory);

        ConcurrentLinkedDeque<Slot<T>> freeSlots;
        if (CollectionUtils.isEmpty(slots)) {
            freeSlots = new ConcurrentLinkedDeque<>();
        } else {
            freeSlots = new ConcurrentLinkedDeque<>(slots);
        }
        this.freeList = freeSlots;
        this.recycler = this::deallocate;

        if (log.isDebugEnabled()) {
            log.debug("new coteriePool: {}", getIdentity());
        }
    }

    public Slot<T> allocate() {

        Slot<T> slot = freeList.pollFirst();

        if (null == slot) {

            int execClaim = ++claimCount, expect = claimPlaner.applyAsInt(execClaim);

            Preconditions.checkPositive(expect, "expect");

            List<Slot<T>> slots = localPool.claim(expect);

            // allow empty ?
            Preconditions.checkArgument(CollectionUtils.isNotEmpty(slots));

            int actual = slots.size();

            if (log.isDebugEnabled()) {
                log.debug("claim slots from localPool, expect={}, actual={}: {}", expect, actual, getIdentity());
            }

            poolMetric.recordClaimFromLocalPool(identity, execClaim, expect, actual);

            slot = slots.remove(actual - 1);

            freeList.addAll(slots);
        }

        slot.setRecycler(recycler);
        pooledObjFactory.activate(slot.internalGet());

        return slot;
    }

    public void deallocate(Slot<T> slot) {
        pooledObjFactory.passivate(slot.internalGet());
        // validate obj or check if destroyed ? depends on normalized use
        // LIFO, otherwise use fifo JCT spsc
        freeList.offerFirst(slot);
    }

    @SuppressWarnings("unchecked")
    public void destroy() {

        Slot<T>[] slots = freeList.toArray(new Slot[0]);

        int slotSize = slots.length;

        freeList.clear();

        localPool.release(CollUtil.newArrayList(slots));

        if (log.isDebugEnabled()) {
            log.debug("release slots to localPool, slots={} : {}", slotSize, getIdentity());
        }

        poolMetric.recordReleaseToLocalPool(identity, slotSize);

    }

    public String getIdentity() {
        return identity.getFullIdentity();
    }

}
