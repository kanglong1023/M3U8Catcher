package io.github.kanglong1023.m3u8.http.pool;

public interface Recycler<T> {

    void recycle(Slot<T> Slot);

}
