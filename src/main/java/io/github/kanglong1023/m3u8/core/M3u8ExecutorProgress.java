package io.github.kanglong1023.m3u8.core;

import io.github.kanglong1023.m3u8.util.CollUtil;
import io.github.kanglong1023.m3u8.util.TextTableFormat;
import io.github.kanglong1023.m3u8.util.Utils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static io.github.kanglong1023.m3u8.util.Preconditions.checkNotNull;
import static io.github.kanglong1023.m3u8.util.Utils.bytesFormat;
import static io.github.kanglong1023.m3u8.util.Utils.secondsFormat;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;

@Slf4j
public class M3u8ExecutorProgress implements Runnable {

    private final StringBuilder out;

    private final TextTableFormat tableFormat;

    private final AtomicLong seconds = new AtomicLong(0);

    private final List<M3u8Progress> m3u8Progresses = CollUtil.newCopyOnWriteArrayList();

    public M3u8ExecutorProgress() {
        StringBuilder out = new StringBuilder();
        TextTableFormat tableFormat = TextTableFormat.textTableFormat(out);
        tableFormat.setTitles("idx", "name", "seconds", "speed", "avgSpeed", "progress", "downloadSize", "estimatedTime",
                "completed", "failed", "reading", "remained");
        tableFormat.setFullWidthGate(1);

        this.out = out;
        this.tableFormat = tableFormat;
    }

    @Override
    public void run() {
        try {
            tableFormat.clearData();

            doProgress();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        } finally {
            tableFormat.clearData();
        }
    }

    private void doProgress() {
        long seconds = this.seconds.incrementAndGet();
        if (CollectionUtils.isEmpty(m3u8Progresses)) {
            return;
        }

        int idx = 0;
        long readBytes = 0;
        final int limitTableSize = 10;
        Deque<M3u8Progress> completes = CollUtil.newArrayDeque();
        List<M3u8Progress> removeItems = CollUtil.newArrayList();
        for (M3u8Progress progress : m3u8Progresses) {
            List<String> data = CollUtil.newArrayListWithCapacity(tableFormat.getTitleSize());
            if (progress.doProgress(++idx, seconds, data)) {
                completes.offer(progress);
            }
            if (idx > limitTableSize) {
                ofNullable(completes.poll()).ifPresent(removeItems::add);
            }
            tableFormat.addData(data);
            readBytes += progress.getReadBytes();

            if (idx % limitTableSize == 0) {
                output(idx == limitTableSize, readBytes);
            }
        }
        if (tableFormat.hasData()) {
            output(idx < limitTableSize, readBytes);
        }
        if (CollectionUtils.isNotEmpty(removeItems)) {
            m3u8Progresses.removeIf(p -> removeItems.stream().anyMatch(s -> s == p));
        }
    }

    public void output(boolean begin, final long readBytes) {
        try {
            tableFormat.print();
            String output = this.out.toString();
            if (begin) {
                String speed = bytesFormat(readBytes, 3);
                String content = format("Running for %s seconds\tspeed %s/s\tList %s M3u8 download tasks: \n%s",
                        seconds, speed, m3u8Progresses.size(), output);
                log.info(content);
            } else {
                String content = format("Continue %s seconds: \n%s", seconds, output);
                log.info(content);
            }
        } finally {
            tableFormat.clearData();
        }
    }

    public void addM3u8(M3u8Download m3u8Download, Future<?> downloadFuture) {
        this.m3u8Progresses.add(new M3u8Progress(checkNotNull(m3u8Download), checkNotNull(downloadFuture), seconds.get()));
    }

    private static class M3u8Progress {

        private final long startSeconds;

        private volatile long endSeconds;

        private final Future<?> downloadFuture;

        private final M3u8Download m3u8Download;

        private final AtomicLong nowBytes = new AtomicLong(0);

        private final AtomicLong lastBytes = new AtomicLong(0);

        private M3u8Progress(M3u8Download m3u8Download, Future<?> downloadFuture, long startSeconds) {
            this.startSeconds = startSeconds;
            this.m3u8Download = checkNotNull(m3u8Download);
            this.downloadFuture = checkNotNull(downloadFuture);
        }

        boolean alreadyEnd() {
            return endSeconds > 0;
        }

        long getReadBytes() {
            if (alreadyEnd()) {
                return 0;
            }
            return Math.max(nowBytes.get() - lastBytes.get(), 0);
        }

        /**
         * @return if complete
         */
        boolean doProgress(int idx, long sec, List<String> data) {
            String fileName = m3u8Download.getFileName();

            if (alreadyEnd()) {
                long endSeconds = this.endSeconds;
                int failedCount = (int) m3u8Download.getFailedTsDownloads();
                int finishedCount = (int) m3u8Download.getFinishedTsDownloads();

                long nowBytes = this.nowBytes.get();
                String avgSpeed = avgSpeed(nowBytes, endSeconds);

                setData(data, idx, fileName, endSeconds, null, avgSpeed,
                        fullRate(), bytesFormat(nowBytes), secondsFormat(0),
                        finishedCount, failedCount, 0, 0);
                return true;
            }

            // timeliness param start
            int remainBytesInReading = 0;
            Set<TsDownload> readingTsDownloads = m3u8Download.getReadingTsDownloads();
            int readingCount = readingTsDownloads.size();
            for (TsDownload tsDownload : readingTsDownloads) {
                long remainingBytes = tsDownload.remainingBytes();
                if (remainingBytes <= 0) {
                    readingCount--;
                } else {
                    remainBytesInReading += remainingBytes;
                }
            }

            long nowBytes = m3u8Download.getDownloadBytes();
            int failedCount = (int) m3u8Download.getFailedTsDownloads();
            int finishedCount = (int) m3u8Download.getFinishedTsDownloads();
            // timeliness param end

            long seconds = sec - startSeconds;
            long lastBytes = this.nowBytes.get();

            this.nowBytes.set(nowBytes);
            this.lastBytes.set(lastBytes);
            long readBytes = Math.max(0, nowBytes - lastBytes);

            String speed = speed(readBytes);
            String avgSpeed = avgSpeed(nowBytes, seconds);

            // "idx", "name", "seconds", "speed", "avgSpeed", "progress", "downloadSize", "estimatedTime",
            // "completed", "failed", "reading", "remained"

            boolean isDone = downloadFuture.isDone();
            if (isDone) {
                long endSeconds = (endSeconds = this.endSeconds) > 0 ? endSeconds : (this.endSeconds = seconds);
                setData(data, idx, fileName, endSeconds, speed, avgSpeed,
                        fullRate(), bytesFormat(nowBytes), secondsFormat(0),
                        finishedCount, failedCount, 0, 0);
                return true;
            }

            int count = m3u8Download.getTsDownloadsCount();
            int remainedCount = count - finishedCount - failedCount - readingCount;
            String progressPercent = rate(finishedCount + failedCount, count);

            long remainingSeconds;
            int conCount = finishedCount + readingCount;
            if (readBytes > 0 && conCount > 0) {
                remainingSeconds = (long) Math.ceil((remainedCount * readBytes * seconds +
                        (long) remainedCount * remainBytesInReading + (long) conCount * remainBytesInReading) /
                        (conCount * readBytes * 1.0));
            } else {
                if (nowBytes <= 0 || conCount <= 0) {
                    remainingSeconds = -1;
                } else {
                    remainingSeconds = (long) Math.ceil((seconds * conCount * remainBytesInReading +
                            seconds * remainedCount * nowBytes + seconds * remainedCount * remainBytesInReading) /
                            (conCount * nowBytes * 1.0));
                }
            }

            String estimatedTime = remainingSeconds > 0 ? secondsFormat(remainingSeconds) : null;

            setData(data, idx, fileName, seconds, speed, avgSpeed,
                    progressPercent, bytesFormat(nowBytes), estimatedTime,
                    finishedCount, failedCount, readingCount, remainedCount);
            return false;
        }

        private String fullRate() {
            return rate(1, 1);
        }

        private String rate(long a, long b) {
            if (0 == a || 0 == b) {
                return "0.000%";
            } else {
                return Utils.rate(a, b) + "%";
            }
        }

        private String bytesFormat(long bytes) {
            return Utils.bytesFormat(bytes, 3);
        }

        private String speed(long bytes) {
            return Utils.bytesFormat(bytes, 3) + "/s";
        }

        private String avgSpeed(long nowBytes, long seconds) {
            BigDecimal bigDecimal = BigDecimal.valueOf(nowBytes)
                    .divide(BigDecimal.valueOf(seconds), 4, RoundingMode.HALF_UP);
            return Utils.bytesFormat(bigDecimal, 3) + "/s";
        }

        private void setData(List<String> data, Object... items) {
            for (Object item : items) {
                if (item instanceof String) {
                    data.add(((String) item));
                } else if (item == null) {
                    data.add(null);
                } else {
                    data.add(item.toString());
                }
            }
        }

    }
}
