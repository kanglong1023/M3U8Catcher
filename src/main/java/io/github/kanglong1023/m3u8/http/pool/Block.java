package io.github.kanglong1023.m3u8.http.pool;

import io.github.kanglong1023.m3u8.util.CollUtil;
import io.github.kanglong1023.m3u8.util.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Block<T> {

    private int size;

    // retain element
    private final ArrayList<Slot<T>> slots;

    public Block(ArrayList<Slot<T>> slots) {
        this.slots = (ArrayList<Slot<T>>) Preconditions.checkNotEmpty(slots);
        this.size = slots.size();
    }

    public int size() {
        return this.size;
    }

    public boolean isEmpty() {
        return this.size == 0;
    }

    public List<Slot<T>> getSlot(int expect) {
        if (expect <= 0 || isEmpty()) {
            return Collections.emptyList();
        }

        final int actual = Math.min(size, expect);
        List<Slot<T>> list = CollUtil.newArrayListWithCapacity(actual);

        for (int i = 0; i < actual; i++) {
            list.add(this.slots.get(--size));
        }

        return list;
    }

    // ----------------- internal method -------------------- //

    List<Slot<T>> getAndRemove() {
        List<Slot<T>> copy = CollUtil.newArrayList(this.slots);
        this.size = 0;
        this.slots.clear();
        return copy;
    }

}
