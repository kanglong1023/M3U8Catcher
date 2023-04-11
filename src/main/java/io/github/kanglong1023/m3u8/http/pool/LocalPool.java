package io.github.kanglong1023.m3u8.http.pool;

import io.github.kanglong1023.m3u8.util.CollUtil;
import io.github.kanglong1023.m3u8.util.Preconditions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.IntUnaryOperator;

import static io.github.kanglong1023.m3u8.util.CollUtil.newArrayDeque;
import static io.github.kanglong1023.m3u8.util.CollUtil.newArrayDequeWithCapacity;

@Slf4j
public class LocalPool<T> {

    private int totalBlocks;

    private final Thread owner;

    private final int slotsPerLink;

    private final int blocksPerReallocate;

    private final int releaseLinkThreshold;

    private final int slotsOfInitialCoterie;

    private final Recycler<T> recycler;

    private final PoolMetric poolMetric;

    private final LongAdder freeListSize;

    private final ScopedIdentity identity;

    private final GlobalPool<T> globalPool;

    private final AtomicBoolean freeListLinkState;

    private final PooledObjFactory<T> pooledObjFactory;

    private final BlockList<T> blockList;

    private final ArrayDeque<Slot<T>> localStack;

    private final ConcurrentLinkedDeque<Slot<T>> freeList;

    public LocalPool(ScopedIdentity identity, GlobalPool<T> globalPool,
                     int blocksPerReallocate, int slotsPerLink, int slotsOfInitialCoterie,
                     PooledObjFactory<T> pooledObjFactory, List<Block<T>> blocks, PoolMetric poolMetric) {

        this.identity = Objects.requireNonNull(identity);
        this.globalPool = Objects.requireNonNull(globalPool);
        this.poolMetric = Objects.requireNonNull(poolMetric);
        this.pooledObjFactory = Objects.requireNonNull(pooledObjFactory);

        this.slotsPerLink = Preconditions.checkPositive(slotsPerLink, "slotsPerLink");
        this.blocksPerReallocate = Preconditions.checkPositive(blocksPerReallocate, "blocksPerReallocate");
        this.slotsOfInitialCoterie = Preconditions.checkNonNegative(slotsOfInitialCoterie, "slotsOfInitialCoterie");
        this.releaseLinkThreshold = slotsPerLink + slotsOfInitialCoterie;

        this.recycler = this::deallocate;
        this.freeListSize = new LongAdder();
        this.owner = Thread.currentThread();
        this.localStack = new ArrayDeque<>();
        this.blockList = new BlockList<>(blocks);
        this.totalBlocks = blockList.size();
        this.freeList = new ConcurrentLinkedDeque<>();
        this.freeListLinkState = new AtomicBoolean(false);
        if (log.isDebugEnabled()) {
            log.debug("new localPool: {}", getIdentity());
        }
    }

    public CoteriePool<T> allocateCoterie(ScopedIdentity identity, IntUnaryOperator claimPlaner) {

        List<Slot<T>> slots = claim(slotsOfInitialCoterie);

        return new CoteriePool<>(identity, this, claimPlaner, pooledObjFactory, slots, poolMetric);
    }

    public Slot<T> allocate() {
        List<Slot<T>> slots = claim(1);
        Slot<T> slot = slots.get(0);

        slot.setRecycler(recycler);
        pooledObjFactory.activate(slot.internalGet());

        return slot;
    }

    public void deallocate(Slot<T> slot) {
        pooledObjFactory.passivate(slot.internalGet());
        release(Collections.singletonList(slot));
    }

    public String getIdentity() {
        return identity.getFullIdentity();
    }

    protected void destroy() {
        if (log.isDebugEnabled()) {
            log.debug("destroy localPool: {}", getIdentity());
        }

        poolMetric.recordDestroyLocalPool(identity, totalBlocks, blockList.size(), blockList.getSlotSizeOfFirstBlock());

        this.totalBlocks = 0;
        this.freeListSize.reset();

        this.freeList.clear();
        this.blockList.clear();
        this.localStack.clear();
    }

    protected List<Slot<T>> claim(final int expect) {
        List<Slot<T>> slots = CollUtil.newArrayListWithCapacity(expect);

        int c = 0;
        Slot<T> slot;
        while (c < expect && null != (slot = localStack.pollFirst())) {
            slots.add(slot);
            c++;
        }
        int diff = expect - slots.size();
        if (diff <= 0) {
            return slots;
        }

        int p = slots.size();
        while (c < expect && null != (slot = freeList.pollFirst())) {
            slots.add(slot);
            c++;
        }
        freeListSize.add(p - slots.size());
        diff = expect - slots.size();
        if (diff <= 0) {
            return slots;
        }

        Link<T> link = globalPool.claim();
        if (null != link) {
            if (log.isDebugEnabled()) {
                log.debug("claim link from globalPool: {}", getIdentity());
            }
            poolMetric.recordClaimLink(identity);
            // assert expect < link.size ?
            slots.addAll(link.getSlot(diff));
            List<Slot<T>> newSlots = link.getAndRemove();
            newSlots.forEach(localStack::offerFirst);
            return slots;
        }

        // form block
        slots.addAll(blockList.getSlot(diff));
        diff = expect - slots.size();
        if (diff <= 0) {
            return slots;
        }

        // from new block
        List<Block<T>> newBlocks = globalPool.allocateBlock(blocksPerReallocate);
        blockList.addBlocks(newBlocks);
        if (log.isDebugEnabled()) {
            log.debug("allocate block from globalPool: {}", getIdentity());
        }
        totalBlocks += newBlocks.size();
        poolMetric.recordReallocateBlock(identity);
        slots.addAll(blockList.getSlot(diff));

        return slots;
    }

    protected void release(List<Slot<T>> c) {
        c = ListUtils.emptyIfNull(c);
        ArrayDeque<Slot<T>> localStack = this.localStack;
        ConcurrentLinkedDeque<Slot<T>> freeList = this.freeList;
        boolean isLocalThread = Thread.currentThread() == owner;
        int releaseSize = c.size(), stackSize = localStack.size();

        if (isLocalThread) {
            // try to reduce call freeListSize.sum()
            if (!freeListLinkState.get() && (releaseSize + stackSize) < releaseLinkThreshold &&
                    (releaseSize + stackSize + this.freeListSize.sum()) < releaseLinkThreshold) {
                c.forEach(localStack::offerFirst);
                return;
            }
        } else {
            if (freeListLinkState.get() || (releaseSize + this.freeListSize.sum()) < releaseLinkThreshold) {
                if (1 == releaseSize) {
                    c.forEach(freeList::offerFirst);
                } else {
                    freeList.addAll(c);
                }
                this.freeListSize.add(releaseSize);
                return;
            }
        }

        // try release link
        int slotsPerLink = this.slotsPerLink;
        if (isLocalThread) {
            Slot<T> slot;
            ArrayDeque<Slot<T>> linkSlots = newArrayDequeWithCapacity(slotsPerLink);
            if (!freeListLinkState.get() && freeListLinkState.compareAndSet(false, true)) {
                int releaseFreeSize = 0;
                while (null != (slot = freeList.pollLast())) {
                    linkSlots.add(slot);
                    releaseFreeSize++;
                    if (slotsPerLink == linkSlots.size()) {
                        globalPool.release(new Link<>(linkSlots));
                        if (log.isDebugEnabled()) {
                            log.debug("release link to globalPool: {}", getIdentity());
                        }
                        linkSlots = newArrayDequeWithCapacity(slotsPerLink);
                    }
                }
                freeListSize.add(-releaseFreeSize);
                freeListLinkState.compareAndSet(true, false);
            }

            c.forEach(localStack::offerFirst);
            linkSlots.forEach(localStack::offerLast);
            stackSize = localStack.size();
            if (stackSize >= slotsPerLink) {
                int count = stackSize, remainingBreak = stackSize % slotsPerLink;
                linkSlots = newArrayDequeWithCapacity(slotsPerLink);
                while (null != (slot = freeList.pollLast())) {
                    linkSlots.add(slot);
                    count--;
                    if (slotsPerLink == linkSlots.size()) {
                        globalPool.release(new Link<>(linkSlots));
                        if (log.isDebugEnabled()) {
                            log.debug("release link to globalPool: {}", getIdentity());
                        }
                        if (count <= remainingBreak) {
                            break;
                        }
                        linkSlots = newArrayDequeWithCapacity(slotsPerLink);
                    }
                }
            }
        } else {
            if (!freeListLinkState.get() && freeListLinkState.compareAndSet(false, true)) {
                Slot<T> slot;
                int releaseFreeSize = 0;
                ArrayDeque<Slot<T>> linkSlots = newArrayDequeWithCapacity(slotsPerLink);
                while (null != (slot = freeList.pollLast())) {
                    linkSlots.add(slot);
                    releaseFreeSize++;
                    if (slotsPerLink == linkSlots.size()) {
                        globalPool.release(new Link<>(linkSlots));
                        linkSlots = newArrayDequeWithCapacity(slotsPerLink);
                    }
                }
                freeListSize.add(-releaseFreeSize);
                freeListLinkState.compareAndSet(true, false);
                if (linkSlots.size() + releaseSize >= slotsPerLink) {
                    ArrayDeque<Slot<T>> deque = newArrayDeque(linkSlots);
                    c.forEach(deque::addFirst);

                    linkSlots = newArrayDequeWithCapacity(slotsPerLink);
                    while (null != (slot = deque.pollLast())) {
                        linkSlots.add(slot);
                        if (slotsPerLink == linkSlots.size()) {
                            globalPool.release(new Link<>(linkSlots));
                            if (log.isDebugEnabled()) {
                                log.debug("release link to globalPool: {}", getIdentity());
                            }
                            linkSlots = newArrayDequeWithCapacity(slotsPerLink);
                        }
                    }

                    int remaining = linkSlots.size();
                    if (remaining > 0) {
                        if (remaining == 1) {
                            freeList.offerFirst(linkSlots.pollFirst());
                        } else {
                            freeList.addAll(linkSlots);
                        }
                    }
                } else {
                    linkSlots.addAll(c);
                    freeList.addAll(linkSlots);
                    freeListSize.add(linkSlots.size());
                }
            } else {
                if (1 == releaseSize) {
                    c.forEach(freeList::offerFirst);
                } else {
                    freeList.addAll(c);
                }
                freeListSize.add(releaseSize);
            }
        }
    }

}
