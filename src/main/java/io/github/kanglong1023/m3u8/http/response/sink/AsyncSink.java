package io.github.kanglong1023.m3u8.http.response.sink;

import io.github.kanglong1023.m3u8.support.shaded.org.jctools.queues.SpscUnboundedArrayQueue;
import io.github.kanglong1023.m3u8.util.CollUtil;
import io.github.kanglong1023.m3u8.util.Preconditions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static io.github.kanglong1023.m3u8.http.response.sink.AsyncSink.State.*;
import static io.github.kanglong1023.m3u8.util.Preconditions.checkNotNull;
import static java.lang.String.format;

@Slf4j
public class AsyncSink implements SinkLifeCycle {

    private final String identity;

    private final Consumer<Runnable> executor;

    private final SinkEventRunner sinkEventRunner;

    private final List<Throwable> asyncExceptions = CollUtil.newCopyOnWriteArrayList();

    public AsyncSink(String identity, Consumer<Runnable> executor) {
        this.identity = checkNotNull(identity);
        this.executor = checkNotNull(executor);
        this.sinkEventRunner = new SinkEventRunner();
    }

    @Override
    public void init(boolean reInit) throws IOException {
        if (reInit) {
            this.sinkEventRunner.clearEvent();
            this.asyncExceptions.clear();
        }
    }

    public void submitAsyncSinkTask(SinkTask sinkTask) throws IOException {
        checkAsyncIOException();
        int size = this.sinkEventRunner.submitSinkTask(sinkTask);
        if (size >= 100) {
            // maybe there's something wrong, log for clues
            log.warn("too much pending event, size={}, identity={}", size, identity);
        }
        if (this.sinkEventRunner.tryReady()) {
            executor.accept(this.sinkEventRunner);
        }
    }

    @Override
    public void dispose() throws IOException {
        checkAsyncIOException();
        Preconditions.checkState(!sinkEventRunner.havePendingTasks(), "have pending tasks: %s", identity);
    }

    private void checkAsyncIOException() throws IOException {
        if (CollectionUtils.isNotEmpty(this.asyncExceptions)) {
            IOException ioException = new IOException(format("async write catch IOException: %s", identity));
            List<Throwable> exceptions = CollUtil.newArrayList(this.asyncExceptions);
            this.asyncExceptions.removeAll(exceptions);
            exceptions.forEach(ioException::addSuppressed);
            throw ioException;
        }
    }

    public interface SinkTask {

        boolean endData();

        void doSink() throws IOException;

        CompletableFuture<Void> completableFuture();

    }

    private class SinkEventRunner implements Runnable {

        private final AtomicReference<State> status;

        private final SpscUnboundedArrayQueue<SinkTask> sinkTaskQueue;

        public SinkEventRunner() {
            this.status = new AtomicReference<>(IDLE);
            this.sinkTaskQueue = new SpscUnboundedArrayQueue<>(1 << 4);
        }

        @Override
        public void run() {
            try {
                if (!this.status.compareAndSet(READY, RUNNING)) {
                    log.warn("withdraw execute, update stata failed");
                    return;
                }
                doSink();
            } catch (Throwable th) {
                log.error(th.getMessage(), th);
            } finally {
                this.status.set(IDLE);
            }
        }

        private void doSink() {
            SinkTask sinkTask;
            Throwable endThrowable = null;
            while ((sinkTask = this.sinkTaskQueue.poll()) != null) {
                try {
                    sinkTask.doSink();
                } catch (Throwable th) {
                    if (sinkTask.endData()) {
                        endThrowable = th;
                    } else {
                        asyncExceptions.add(th);
                    }
                } finally {
                    if (sinkTask.endData() && null != endThrowable) {
                        sinkTask.completableFuture().completeExceptionally(endThrowable);
                    } else {
                        sinkTask.completableFuture().complete(null);
                    }
                }
            }
        }

        public void clearEvent() {
            this.sinkTaskQueue.clear();
        }

        public boolean tryReady() {
            return this.status.compareAndSet(IDLE, READY);
        }

        public int submitSinkTask(SinkTask sinkTask) {
            this.sinkTaskQueue.offer(checkNotNull(sinkTask));
            return this.sinkTaskQueue.size();
        }

        public boolean havePendingTasks() {
            return !this.sinkTaskQueue.isEmpty();
        }

    }

    public enum State {
        IDLE, READY, RUNNING;
    }

}
