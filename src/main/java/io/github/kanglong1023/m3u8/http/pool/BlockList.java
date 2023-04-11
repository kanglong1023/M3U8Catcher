package io.github.kanglong1023.m3u8.http.pool;

import io.github.kanglong1023.m3u8.util.CollUtil;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class BlockList<T> {

    private final ArrayDeque<Block<T>> blockList;

    public BlockList() {
        this(null);
    }

    public BlockList(Collection<Block<T>> blocks) {
        ArrayDeque<Block<T>> blockList;
        if (CollectionUtils.isEmpty(blocks)) {
            blockList = CollUtil.newArrayDeque();
        } else {
            blockList = CollUtil.newArrayDeque(blocks);
        }
        this.blockList = blockList;
    }

    public int size() {
        return blockList.size();
    }

    public boolean isEmpty() {
        return blockList.isEmpty();
    }

    public List<Slot<T>> getSlot(final int expect) {
        Block<T> block = blockList.peekFirst();
        if (null == block) {
            return Collections.emptyList();
        }

        int diff = expect;
        List<Slot<T>> slots = CollUtil.newArrayListWithCapacity(expect);

        do {
            // assert expect < slotsPerBlock ?
            slots.addAll(block.getSlot(diff));
            if (block.isEmpty()) {
                blockList.pollFirst();
            }
            diff = expect - slots.size();
            if (diff <= 0) {
                return slots;
            }
        } while (null != (block = blockList.peekFirst()));

        return slots;
    }

    public void addBlocks(List<Block<T>> blocks) {
        for (Block<T> block : CollectionUtils.emptyIfNull(blocks)) {
            if (null != block) {
                blockList.offerLast(block);
            }
        }
    }

    public void clear() {
        blockList.clear();
    }

    int getSlotSizeOfFirstBlock() {
        Block<T> block = blockList.peekFirst();
        if (null == block) {
            return 0;
        }
        return block.size();
    }

}
