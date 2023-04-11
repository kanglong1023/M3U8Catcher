package io.github.kanglong1023.m3u8.http.pool;

import io.github.kanglong1023.m3u8.util.CollUtil;
import io.github.kanglong1023.m3u8.util.Preconditions;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class GlobalPool<T> {

    // normal = 0, destroyed = -1
    private volatile int state = 0;

    private final PoolConfig poolConfig;

    private final PoolMetric poolMetric;

    private final ScopedIdentity identity;

    private final Lock lock = new ReentrantLock();

    private final PooledObjFactory<T> pooledObjFactory;

    // --------------------- pooled obj refer --------------------- //

    private final ChunkList<T> chunkList;

    private final ArrayList<Chunk<T>> allChunks;

    private final ConcurrentLinkedDeque<Link<T>> linkQueue;

    public GlobalPool(ScopedIdentity identity, PooledObjFactory<T> pooledObjFactory,
                      PoolConfig poolConfig, PoolMetric poolMetric) {
        this.identity = Objects.requireNonNull(identity);
        this.poolMetric = Objects.requireNonNull(poolMetric);
        this.poolConfig = Objects.requireNonNull(poolConfig);
        this.pooledObjFactory = Objects.requireNonNull(pooledObjFactory);

        this.chunkList = new ChunkList<>();
        this.allChunks = new ArrayList<>();
        this.linkQueue = new ConcurrentLinkedDeque<>();

        final int initialChunks = poolConfig.chunksOfInitialGlobalPool();
        for (int i = 0; i < initialChunks; i++) {
            Chunk<T> chunk = newChunk();
            allChunks.add(chunk);
        }
        chunkList.addChunks(allChunks);
        if (log.isDebugEnabled()) {
            log.debug("init new chunk, initialChunks={}: {}", initialChunks, getIdentity());
        }
    }

    protected Link<T> claim() {
        return linkQueue.pollFirst();
    }

    protected void release(Link<T> link) {
        // validate obj ?
        linkQueue.offerFirst(link);
    }

    protected List<Block<T>> allocateBlock(final int expect) {
        Preconditions.checkState(0 == state, "GlobalPool is destroyed: %s", getIdentity());
        lock.lock();
        boolean makeChunk = false;
        try {
            List<Block<T>> blocks = chunkList.getBlock(expect);
            int diff = expect - blocks.size();
            if (diff <= 0) {
                return blocks;
            }

            Chunk<T> newChunk = newChunk();
            chunkList.addChunk(newChunk);
            makeChunk = allChunks.add(newChunk);

            List<Block<T>> res = CollUtil.newArrayListWithCapacity(expect);
            res.addAll(blocks);
            res.addAll(chunkList.getBlock(diff));
            return res;
        } finally {
            lock.unlock();
            if (makeChunk) {
                if (log.isDebugEnabled()) {
                    log.debug("new chunk: {}", getIdentity());
                }
                poolMetric.recordNewChunk(identity);
            }
        }
    }

    private Chunk<T> newChunk() {
        int slotsPerBlock = poolConfig.slotsPerBlock();
        int blocksPerChunk = poolConfig.blocksPerChunk();
        ArrayList<Block<T>> blocks = CollUtil.newArrayListWithCapacity(blocksPerChunk);

        for (int i = 0; i < blocksPerChunk; i++) {
            List<T> objs = pooledObjFactory.newInstance(slotsPerBlock);
            ArrayList<Slot<T>> slots = CollUtil.newArrayListWithCapacity(objs.size());
            for (T obj : objs) {
                slots.add(new Slot<>(obj));
            }
            blocks.add(new Block<>(slots));
        }
        return new Chunk<>(blocks);
    }

    public synchronized void destroy() {
        if (-1 == state) {
            return;
        }
        state = -1;
        log.info("free globalPool: {}", getIdentity());

        poolMetric.recordDestroyGlobalPool(identity, allChunks.size(), chunkList.size(), chunkList.getBlockSizeOfFirstChunk());

        for (Chunk<T> chunk : allChunks) {
            List<Block<T>> blocks = chunk.getAndRemove();
            for (Block<T> block : blocks) {
                List<Slot<T>> slots = block.getAndRemove();
                List<T> objs = CollUtil.newArrayListWithCapacity(slots.size());
                for (Slot<T> slot : slots) {
                    T o = slot.getAndRemove();
                    if (null != o) {
                        objs.add(o);
                    }
                }
                pooledObjFactory.free(objs);
            }
        }

        allChunks.clear();
        linkQueue.clear();
        chunkList.clear();
    }

    public String getIdentity() {
        return identity.getFullIdentity();
    }

    public ScopedIdentity getScopedIdentity() {
        return identity;
    }

}
