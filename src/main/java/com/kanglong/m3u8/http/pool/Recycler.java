package com.kanglong.m3u8.http.pool;

public interface Recycler<T> {

    void recycle(Slot<T> Slot);

}
