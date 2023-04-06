package com.kanglong.m3u8.core;

import com.kanglong.m3u8.http.config.HttpRequestConfig;
import com.kanglong.m3u8.util.CollUtil;
import com.kanglong.m3u8.util.Preconditions;
import com.kanglong.m3u8.util.Utils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.kanglong.m3u8.core.M3u8HttpRequestType.*;
import static com.kanglong.m3u8.util.Preconditions.*;

@Slf4j
@Getter
public class M3u8Resolver {

    private final URI m3u8Uri;

    private final M3u8HttpRequestConfigStrategy requestConfigStrategy;

    private final BiFunction<URI, HttpRequestConfig, ByteBuffer> bytesResponseGetter;

    // ----------- result ------------ //

    private URI finalM3u8Uri;

    private URI masterM3u8Uri;

    private String finalM3u8Content;

    private String masterM3u8Content;

    private List<MediaSegment> mediaSegments;

    public M3u8Resolver(URI m3u8Uri, M3u8HttpRequestConfigStrategy requestConfigStrategy,
                        BiFunction<URI, HttpRequestConfig, ByteBuffer> bytesResponseGetter) {
        this.m3u8Uri = checkNotNull(m3u8Uri);
        this.requestConfigStrategy = requestConfigStrategy;
        this.bytesResponseGetter = checkNotNull(bytesResponseGetter);
    }

    public Map<MediaSegment, M3u8SecretKey> fetchSecretKey(List<MediaSegment> segments) {
        segments = segments.stream().filter(s -> Objects.nonNull(s.getKey())).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(segments)) {
            return Collections.emptyMap();
        }

        Map<MediaSegmentKey, M3u8SecretKey> cache = CollUtil.newHashMap();
        Map<MediaSegment, M3u8SecretKey> result = CollUtil.newLinkedHashMap();
        for (MediaSegment segment : segments) {
            MediaSegmentKey key = segment.getKey();
            if (Objects.isNull(key)) {
                continue;
            }
            if (cache.containsKey(key)) {
                result.put(segment, cache.get(key).copy());
                continue;
            }
            if (Objects.equals("NONE", key.getMethod())) {
                result.put(segment, M3u8SecretKey.NONE);
                continue;
            }
            if (Objects.equals("AES-128", key.getMethod())) {
                String iv = key.getIv();
                URI keyUri = key.getUri();
                String keyFormat = key.getKeyFormat();
                m3u8CheckNotNull(keyUri, "key uri is null: %s", key);

                String keyMethod = "AES-128";
                byte[] keyBytes = new byte[16];
                HttpRequestConfig requestConfig = getConfig(REQ_FOR_KEY, keyUri);
                ByteBuffer byteBuffer = bytesResponseGetter.apply(keyUri, requestConfig);
                if (byteBuffer.remaining() >= 16) {
                    byteBuffer.get(keyBytes);
                } else {
                    m3u8Exception("keyBytes len < 16: %s", key);
                }

                byte[] initVector = new byte[16];
                if (StringUtils.isBlank(keyFormat) || Objects.equals("identity", keyFormat)) {
                    if (StringUtils.isBlank(iv)) {
                        Integer sequence = segment.getSequence();
                        if (Objects.nonNull(sequence)) {
                            initVector = sequenceToBytes(sequence);
                        }
                    } else if (iv.startsWith("0x") || iv.startsWith("0X")) {
                        initVector = Utils.parseHexadecimal(iv);
                    }
                    M3u8SecretKey m3u8SecretKey = new M3u8SecretKey(keyBytes, initVector, keyMethod);
                    cache.put(key, m3u8SecretKey);
                    result.put(segment, m3u8SecretKey);
                    continue;
                } else {
                    m3u8Exception("unSupported keyFormat: %s", key);
                }
            }
            m3u8Exception("unSupported key method: %s", key);
        }
        return result;
    }

    public byte[] sequenceToBytes(Integer sequence) {
        Preconditions.checkNotNull(sequence);
        Preconditions.checkArgument(sequence >= 0);

//        String hexString = Integer.toHexString(sequence);
//        if ((hexString.length() & 1) != 0) {
//            hexString = "0x0" + hexString;
//        } else {
//            hexString = "0x" + hexString;
//        }
//
//        byte[] bytes = Utils.parseHexadecimal(hexString);

        // default iv is sequence ?
        // big-endian
//        int bLen = bytes.length;
        byte[] res = new byte[16];
//        for (int i = 16 - bLen, j = 0; j < bLen; ++i, ++j) {
//            res[i] = bytes[j];
//        }
        return res;
    }

    public void resolve() {
        doResolve(this.m3u8Uri, null, false);
    }

    private void doResolve(URI m3u8Uri, MediaSegmentKey segmentKey, final boolean secondaryStream) {
        Preconditions.checkNotNull(m3u8Uri);

        Supplier<String> m3u8ContentSupplier = () -> {
            HttpRequestConfig requestConfig = getConfig(secondaryStream ?
                    REQ_FOR_VARIANT_PLAYLIST : REQ_FOR_M3U8_CONTENT, m3u8Uri);
            return bytesResponseGetter.andThen(
                    buffer -> new String(buffer.array(), buffer.arrayOffset() + buffer.position(),
                            buffer.remaining(), StandardCharsets.UTF_8)).apply(m3u8Uri, requestConfig);
        };

        String url = m3u8Uri.toString();
        String m3u8Content = m3u8ContentSupplier.get();
        log.info("{} get content: \n{}", url, m3u8Content);

        String[] lineAry = m3u8Content.split("\\n");
        m3u8Check(lineAry[0].startsWith("#EXTM3U"), "not m3u8: %s", url);

        int sequenceNumber = 0;
        String variantStreamInf = null, extInf = null;
        List<MediaSegment> mediaSegments = CollUtil.newArrayList();
        Map<URI, Map<String, String>> variantStreamUriAttrMap = CollUtil.newLinkedHashMap();
        for (String line : lineAry) {
            line = line.trim();
            // ignore blank and comments
            if (StringUtils.isBlank(line) || (line.startsWith("#") && !line.startsWith("#EXT"))) {
                continue;
            }
            // version
            if (line.startsWith("#EXT-X-VERSION")) {
                String[] strAry = line.split(":");
                if (strAry.length == 2 && strAry[1].length() > 0) {
                    int version = Integer.parseInt(strAry[1].trim());
                    if (version > 3) {
                        log.warn("compatible version is HLS 3, the current HLS version is {}, some functions are not supported", version);
                    }
                }
                continue;
            }
            // variant stream
            // check secondaryStream prevent circle error
            if (!secondaryStream && line.startsWith("#EXT-X-STREAM-INF")) {
                variantStreamInf = line;
                continue;
            }
            // sequence
            if (line.startsWith("#EXT-X-MEDIA-SEQUENCE")) {
                String[] strAry = line.split(":");
                if (strAry.length == 2 && strAry[1].length() > 0) {
                    sequenceNumber = Integer.parseInt(strAry[1].trim());
                }
                continue;
            }
            // key
            if (line.startsWith("#EXT-X-KEY") || line.startsWith("#EXT-X-SESSION-KEY")) {
                segmentKey = resolveKey(line);
                continue;
            }
            // media segment
            if (line.startsWith("#EXTINF")) {
                extInf = line;
                continue;
            }
            // uri
            if (!line.startsWith("#")) {
                // variant stream uri
                if (null != variantStreamInf) {
                    URI variantStream = URI.create(line);
                    Map<String, String> attrMap = CollUtil.newLinkedHashMap();
                    if (!variantStream.isAbsolute()) {
                        variantStream = m3u8Uri.resolve(variantStream);
                    }

                    String attrString;
                    int index = variantStreamInf.indexOf(':');
                    if (index > 0 && index < variantStreamInf.length() - 1
                            && (attrString = variantStreamInf.substring(index + 1)).length() > 0) {
                        String[] attrAry = attrString.split(",");
                        if (attrAry.length > 0) {
                            for (int i = 0; i < attrAry.length; i++) {
                                attrAry[i] = attrAry[i].trim();
                                String[] attr = attrAry[i].split("=");
                                if (attr.length == 2 && attr[1].length() > 0) {
                                    attrMap.put(attr[0].trim(), removeQuotes(attr[1]));
                                }
                            }
                        }
                    }
                    variantStreamUriAttrMap.put(variantStream, attrMap);
                    variantStreamInf = null;
                    continue;
                }
                // ts
                if (null != extInf) {
                    URI mediaUri = URI.create(line);
                    if (!mediaUri.isAbsolute()) {
                        mediaUri = m3u8Uri.resolve(mediaUri);
                    }
                    String attrString;
                    Double durationInSeconds = null;
                    int index = extInf.indexOf(':');
                    if (index > 0 && index < extInf.length() - 1
                            && (attrString = extInf.substring(index + 1)).length() > 0) {
                        String[] attrAry = attrString.split(",");
                        if (attrAry.length > 0) {
                            durationInSeconds = Double.valueOf(attrAry[0].trim());
                        }
                    }
                    extInf = null;

                    MediaSegment mediaSegment = new MediaSegment();
                    mediaSegment.setUri(mediaUri);
                    mediaSegment.setKey(segmentKey);
                    mediaSegment.setSequence(sequenceNumber++);
                    mediaSegment.setDurationInSeconds(durationInSeconds);

                    mediaSegments.add(mediaSegment);
                    continue;
                }
                log.debug("ignore uri={}", line);
            }
        }

        if (MapUtils.isNotEmpty(variantStreamUriAttrMap)) {
            log.info("variant playlist: \n{}", variantStreamUriAttrMap);
            URI matchedUri = selectVariantStreamUri(variantStreamUriAttrMap);
            m3u8Check(Objects.nonNull(matchedUri), "select null variant stream uri");

            this.masterM3u8Uri = m3u8Uri;
            this.masterM3u8Content = m3u8Content;
            String matchedUrl = matchedUri.toString();
            log.info("variant playlist match {}", matchedUrl);

            doResolve(matchedUri, segmentKey, true);
            return;
        }

        if (CollectionUtils.isNotEmpty(mediaSegments)) {
            if (log.isDebugEnabled()) {
                log.debug("media segments: \n{}", mediaSegments);
            }
            this.finalM3u8Uri = m3u8Uri;
            this.finalM3u8Content = m3u8Content;

            this.mediaSegments = mediaSegments;
            return;
        }

        log.warn("resolve empty mediaSegments");
    }

    private URI selectVariantStreamUri(Map<URI, Map<String, String>> variantStreamUriAttrMap) {
        // default is the first one, support selector ?
        return variantStreamUriAttrMap.entrySet().iterator().next().getKey();
    }

    private HttpRequestConfig getConfig(M3u8HttpRequestType requestType, URI uri) {
        Preconditions.checkNotNull(uri);
        Preconditions.checkNotNull(requestType);

        M3u8HttpRequestConfigStrategy configStrategy = this.requestConfigStrategy;
        if (Objects.nonNull(configStrategy)) {
            return configStrategy.getConfig(requestType, uri);
        }
        return null;
    }

    private String removeQuotes(String str) {
        if (null == str || 0 == (str = str.trim()).length()) {
            return str;
        }

        boolean sub = false;
        int len = str.length(), beginIndex = 0, endIndex = len;
        if (str.endsWith("\"")) {
            sub = true;
            endIndex = len - 1;
        }
        if (str.startsWith("\"")) {
            sub = true;
            beginIndex = 1;
        }
        if (sub) {
            str = str.substring(beginIndex, endIndex);
        }
        return str;
    }

    private MediaSegmentKey resolveKey(String extXKey) {
        String attrString;
        int index = extXKey.indexOf(':');
        if (index > 0 && index < extXKey.length() - 1
                && (attrString = extXKey.substring(index + 1)).length() > 0) {
            String[] attrAry = attrString.split(",");
            if (attrAry.length > 0) {
                MediaSegmentKey key = new MediaSegmentKey();
                for (int i = 0; i < attrAry.length; i++) {
                    attrAry[i] = attrAry[i].trim();
                    String[] attr = attrAry[i].split("=");
                    if (attr.length == 2 && attr[1].length() > 0) {
                        String attrName = attr[0].trim();
                        String val = removeQuotes(attr[1]);
                        if (attrName.startsWith("METHOD")) {
                            key.setMethod(val);
                            continue;
                        }
                        if (attrName.startsWith("URI")) {
                            key.setUri(URI.create(val));
                            continue;
                        }
                        if (attrName.startsWith("IV")) {
                            key.setIv(val);
                            continue;
                        }
                        if (attrName.startsWith("KEYFORMAT")) {
                            key.setKeyFormat(val);
                            continue;
                        }
                        if (attrName.startsWith("KEYFORMATVERSIONS")) {
                            key.setKeyFormatVersions(val);
                            continue;
                        }
                    }
                }
                return key;
            }
        }
        return null;
    }


    @Getter
    @Setter
    @ToString
    @EqualsAndHashCode
    public static class MediaSegment {

        private URI uri;

        private Integer sequence;

        private MediaSegmentKey key;

        private Double durationInSeconds;

    }

    @Getter
    @Setter
    @ToString
    @EqualsAndHashCode
    public static class MediaSegmentKey {

        private URI uri;

        private String iv;

        private String method;

        private String keyFormat;

        private String keyFormatVersions;

    }

}
