package io.github.kanglong1023.m3u8.http.pool;

import io.github.kanglong1023.m3u8.util.CollUtil;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ChunkList<T> {

    private final ArrayDeque<Chunk<T>> chunkList;

    public ChunkList() {
        this(null);
    }

    public ChunkList(Collection<Chunk<T>> chunks) {
        ArrayDeque<Chunk<T>> chunkList;
        if (CollectionUtils.isEmpty(chunks)) {
            chunkList = CollUtil.newArrayDeque();
        } else {
            chunkList = CollUtil.newArrayDeque(chunks);
        }
        this.chunkList = chunkList;
    }

    public int size() {
        return chunkList.size();
    }

    public boolean isEmpty() {
        return chunkList.isEmpty();
    }

    public List<Block<T>> getBlock(final int expect) {
        Chunk<T> chunk = chunkList.peekFirst();
        if (null == chunk) {
            return Collections.emptyList();
        }

        int diff = expect;
        List<Block<T>> blocks = CollUtil.newArrayListWithCapacity(expect);

        do {
            // assert expect <= blocksPerChunk ?
            blocks.addAll(chunk.getBlock(diff));
            if (chunk.isEmpty()) {
                chunkList.pollFirst();
            }
            diff = expect - blocks.size();
            if (diff <= 0) {
                return blocks;
            }
        } while (null != (chunk = chunkList.peekFirst()));

        return blocks;
    }

    public void addChunk(Chunk<T> chunk) {
        addChunks(Collections.singletonList(chunk));
    }

    public void addChunks(List<Chunk<T>> chunks) {
        for (Chunk<T> chunk : CollectionUtils.emptyIfNull(chunks)) {
            if (null != chunk) {
                chunkList.offerLast(chunk);
            }
        }
    }

    public void clear() {
        chunkList.clear();
    }

    int getBlockSizeOfFirstChunk() {
        Chunk<T> chunk = chunkList.peekFirst();
        if (null == chunk) {
            return 0;
        }
        return chunk.size();
    }

}