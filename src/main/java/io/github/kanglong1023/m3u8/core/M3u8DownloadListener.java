package io.github.kanglong1023.m3u8.core;

import io.github.kanglong1023.m3u8.util.CollUtil;
import io.github.kanglong1023.m3u8.util.Preconditions;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.lang.String.format;

public interface M3u8DownloadListener {

    default void start(M3u8Download m3u8Download) {
    }

    default void end(M3u8Download m3u8Download) {
    }

    @Slf4j
    class M3u8DownloadListeners implements M3u8DownloadListener {

        private final M3u8Download m3u8Download;

        private final List<M3u8DownloadListener> listeners;

        public M3u8DownloadListeners(M3u8Download m3u8Download, List<M3u8DownloadListener> listeners) {
            this.m3u8Download = Preconditions.checkNotNull(m3u8Download);
            this.listeners = null == listeners ? CollUtil.newCopyOnWriteArrayList() :
                    listeners.stream().filter(Objects::nonNull).collect(Collectors.toCollection(CollUtil::newCopyOnWriteArrayList));
        }

        @Override
        public void start(M3u8Download m3u8Download) {
            for (M3u8DownloadListener listener : this.listeners) {
                try {
                    listener.start(m3u8Download);
                } catch (Exception e) {
                    log.error(format("execute listener start error(m3u8Download=%s): %s", m3u8Download, listener), e);
                }
            }
        }

        @Override
        public void end(M3u8Download m3u8Download) {
            for (M3u8DownloadListener listener : this.listeners) {
                try {
                    listener.end(m3u8Download);
                } catch (Exception e) {
                    log.error(format("execute listener start error(m3u8Download=%s): %s", m3u8Download, listener), e);
                }
            }
        }

        public void addListener(M3u8DownloadListener listener) {
            this.listeners.add(Preconditions.checkNotNull(listener));
        }
    }

}
