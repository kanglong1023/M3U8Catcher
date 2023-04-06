package com.kanglong.m3u8.core;

import com.kanglong.m3u8.http.response.FileDownloadOptions;
import com.kanglong.m3u8.util.Preconditions;
import org.apache.commons.collections4.CollectionUtils;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;

import static java.util.Optional.ofNullable;

public interface TsDownloadOptionsSelector {

    org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TsDownloadOptionsSelector.class);

    default FileDownloadOptions getDownloadOptions(M3u8Download m3u8Download, List<TsDownload> tsDownloads) {
        Preconditions.checkNotNull(m3u8Download);
        if (CollectionUtils.isEmpty(tsDownloads)) {
            return null;
        }

        // spec: loop back addr
        URI uri = tsDownloads.get(0).getUri();
        InetSocketAddress inetSocketAddress = new InetSocketAddress(uri.getHost(), 80);
        if (ofNullable(inetSocketAddress.getAddress()).filter(InetAddress::isLoopbackAddress).isPresent()) {
            log.info("loopback addr, use false, identity={}, addr={}", m3u8Download.getIdentity(), inetSocketAddress.getAddress());
            return FileDownloadOptions.getInstance(false, false);
        }
        return getDownloadOptionsInternal(m3u8Download, tsDownloads);
    }


    FileDownloadOptions getDownloadOptionsInternal(M3u8Download m3u8Download, List<TsDownload> tsDownloads);

    class PlainTsDownloadOptionsSelector implements TsDownloadOptionsSelector {

        private final FileDownloadOptions fileDownloadOptions;

        public PlainTsDownloadOptionsSelector(FileDownloadOptions fileDownloadOptions) {
            this.fileDownloadOptions = Preconditions.checkNotNull(fileDownloadOptions);
        }

        @Override
        public FileDownloadOptions getDownloadOptionsInternal(M3u8Download m3u8Download, List<TsDownload> tsDownloads) {
            return this.fileDownloadOptions;
        }

        public static PlainTsDownloadOptionsSelector optionsSelector(boolean ifAsyncSink,
                                                                     boolean useBufferPool) {
            return new PlainTsDownloadOptionsSelector(FileDownloadOptions.getInstance(ifAsyncSink, useBufferPool));
        }

    }


}

