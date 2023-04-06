package com.kanglong.m3u8.http.pool;

import com.kanglong.m3u8.util.CollUtil;
import com.kanglong.m3u8.util.Preconditions;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;

public class Link<T> {

    private final ArrayDeque<Slot<T>> slots;

    public Link(ArrayDeque<Slot<T>> slots) {
        this.slots = (ArrayDeque<Slot<T>>) Preconditions.checkNotEmpty(slots);
    }

    public int size() {
        return this.slots.size();
    }

    public boolean isEmpty() {
        return this.slots.isEmpty();
    }

    public List<Slot<T>> getSlot(final int expect) {
        int size;
        if (expect <= 0 || 0 == (size = this.slots.size())) {
            return Collections.emptyList();
        }

        final int actual = Math.min(size, expect);
        List<Slot<T>> list = CollUtil.newArrayListWithCapacity(actual);

        int c = 0;
        Slot<T> slot;
        while (c++ < actual && null != (slot = slots.pollFirst())) {
            list.add(slot);
        }

        return list;
    }

    // ----------------- internal method -------------------- //

    List<Slot<T>> getAndRemove() {
        List<Slot<T>> copy = CollUtil.newArrayList(this.slots);
        this.slots.clear();
        return copy;
    }

}
