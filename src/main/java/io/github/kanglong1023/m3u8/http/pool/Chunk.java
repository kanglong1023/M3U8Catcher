package io.github.kanglong1023.m3u8.http.pool;

import io.github.kanglong1023.m3u8.util.CollUtil;
import io.github.kanglong1023.m3u8.util.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Chunk<T> {

    private int size;

    // retain element
    private final ArrayList<Block<T>> blocks;

    public Chunk(ArrayList<Block<T>> blocks) {
        this.blocks = (ArrayList<Block<T>>) Preconditions.checkNotEmpty(blocks);
        this.size = blocks.size();
    }

    public int size() {
        return this.size;
    }

    public boolean isEmpty() {
        return this.size == 0;
    }

    public List<Block<T>> getBlock(final int expect) {
        if (expect <= 0 || isEmpty()) {
            return Collections.emptyList();
        }

        final int actual = Math.min(size, expect);
        List<Block<T>> list = CollUtil.newArrayListWithCapacity(actual);

        for (int i = 0; i < actual; i++) {
            list.add(this.blocks.get(--size));
        }

        return list;
    }

    // ----------------- internal method -------------------- //

    List<Block<T>> getAndRemove() {
        List<Block<T>> copy = CollUtil.newArrayList(this.blocks);
        this.size = 0;
        this.blocks.clear();
        return copy;
    }

}
