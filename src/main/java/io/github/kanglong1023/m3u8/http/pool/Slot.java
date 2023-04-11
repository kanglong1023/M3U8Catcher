package io.github.kanglong1023.m3u8.http.pool;

import io.github.kanglong1023.m3u8.util.Utils;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

@Slf4j
public class Slot<T> {

    private T val;

    private volatile Recycler<T> recycler;

    public Slot(T val) {
        this.val = Objects.requireNonNull(val);
    }

    public T get() {
        if (isRecycled()) {
            if (log.isDebugEnabled()) {
                log.debug("found {} isRecycled, invoke in {}", this, Utils.getPreviousStackTrace(1));
            }
            return null;
        }
        return val;
    }

    public void recycle() {
        if (isRecycled()) {
            return;
        }
        Recycler<T> r = recycler;
        this.recycler = null;
        r.recycle(this);
    }

    public boolean isRecycled() {
        return null == recycler;
    }

    // ----------------- internal method -------------------- //

    void setRecycler(Recycler<T> recycler) {
        this.recycler = Objects.requireNonNull(recycler);
    }

    T internalGet() {
        return this.val;
    }

    T getAndRemove() {
        T v = this.val;
        this.val = null;
        this.recycler = null;
        return v;
    }

}
