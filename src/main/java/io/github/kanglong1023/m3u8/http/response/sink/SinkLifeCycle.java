package io.github.kanglong1023.m3u8.http.response.sink;

import java.io.IOException;

interface SinkLifeCycle {

    default void init(boolean reInit) throws IOException {
    }

    default void dispose() throws IOException {
    }

}
