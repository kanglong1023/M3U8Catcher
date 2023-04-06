package com.kanglong.m3u8.http.pool;

import java.nio.ByteBuffer;
import java.util.List;

public interface PooledObjFactory<T> {

    T newInstance();

    Class<T> getType();

    List<T> newInstance(int size);

    default boolean validate(ByteBuffer buffer) {
        return true;
    }

    default void activate(T obj) {
    }

    default void passivate(T obj) {
    }

    default void free(T obj) {
    }

    default void free(List<T> objs) {
    }

}
