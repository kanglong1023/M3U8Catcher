package io.github.kanglong1023.m3u8.util;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.function.Function;

import static io.github.kanglong1023.m3u8.util.Preconditions.m3u8Check;

public final class Utils {

    public static final ByteBuffer EMPTY_BIN = ByteBuffer.wrap(new byte[0]);

    private static final char SLASH = '/';

    private static final char BACKSLASH = '\\';

    private static final CharSequence[] SPECIAL_SUFFIX = {"tar.bz2", "tar.Z", "tar.gz", "tar.xz"};

    private Utils() {
    }

    private static boolean isFileSeparator(char c) {
        return SLASH == c || BACKSLASH == c;
    }

    public static String mainName(String fileName) {
        if (null == fileName) {
            return null;
        }
        int len = fileName.length();
        if (0 == len) {
            return fileName;
        }

        for (final CharSequence specialSuffix : SPECIAL_SUFFIX) {
            if (fileName.endsWith("." + specialSuffix)) {
                return fileName.substring(0, len - specialSuffix.length() - 1);
            }
        }

        if (isFileSeparator(fileName.charAt(len - 1))) {
            len--;
        }

        char c;
        int begin = 0, end = len;
        for (int i = len - 1; i >= 0; i--) {
            c = fileName.charAt(i);
            if (len == end && '.' == c) {
                end = i;
            }
            if (isFileSeparator(c)) {
                begin = i + 1;
                break;
            }
        }

        return fileName.substring(begin, end);
    }

    public static String secondsFormat(long seconds) {
        if (seconds <= 0) {
            return "0sec";
        }

        long hours = seconds / 3600;
        int minutes = (int) ((seconds % 3600) / 60);
        int secs = (int) (seconds % 60);

        StringBuilder buf = new StringBuilder();
        if (hours != 0) {
            buf.append(hours).append("hour");
        }
        if (minutes != 0) {
            buf.append(minutes).append("min");
        }
        if (secs != 0 || buf.length() == 0) {
            buf.append(secs).append("sec");
        }
        return buf.toString();
    }

    public static String genIdentity(URI uri) {
        Preconditions.checkNotNull(uri);
        String suffix = uri.getPath();
        if (suffix.length() > 50) {
            suffix = Paths.get(suffix).getFileName().toString();
        }
        return uri.getHost() + ":" + suffix;
    }

    public static String bytesFormat(long v, int scale) {
        if (v < 1024) {
            return v + " B";
        }
        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
        BigDecimal decimal = BigDecimal.valueOf(v).divide(BigDecimal.valueOf((1L << (z * 10))), scale, RoundingMode.HALF_UP);
        return String.format("%s %sB", decimal, "BKMGTPE".charAt(z));
    }

    public static String bytesFormat(BigDecimal bigDecimal, int scale) {
        Preconditions.checkNotNull(bigDecimal);
        return bytesFormat(bigDecimal.longValue(), scale);
    }

    public static boolean equals(BigDecimal bigNum1, BigDecimal bigNum2) {
        if (bigNum1 == bigNum2) {
            return true;
        }
        if (bigNum1 == null || bigNum2 == null) {
            return false;
        }
        return 0 == bigNum1.compareTo(bigNum2);
    }

    public static boolean notEquals(BigDecimal bigNum1, BigDecimal bigNum2) {
        return !equals(bigNum1, bigNum2);
    }

    public static byte[] parseHexadecimal(String hexString) {
        Preconditions.checkNotBlank(hexString);
        Preconditions.checkArgument(hexString.startsWith("0x") || hexString.startsWith("0X"));

        int length = hexString.length();
        Preconditions.checkArgument((length & 1) == 0, "invalid Hexadecimal string");

        byte[] bytes = new byte[(length - 2) / 2];
        for (int i = 2, j = 0; i < length; i += 2, ++j) {
            bytes[j] = (byte) (Short.parseShort(hexString.substring(i, i + 2), 16) & 0xFF);
        }
        return bytes;
    }

    public static String md5(String str) {
        MessageDigest messageDigest;

        try {
            messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.reset();
            messageDigest.update(str.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        byte[] byteArray = messageDigest.digest();
        StringBuilder md5StrBuff = new StringBuilder();
        for (byte b : byteArray) {
            if (Integer.toHexString(0xFF & b).length() == 1) {
                md5StrBuff.append("0").append(Integer.toHexString(0xFF & b));
            } else {
                md5StrBuff.append(Integer.toHexString(0xFF & b));
            }
        }
        return md5StrBuff.toString();
    }

    public static boolean isFileNameTooLong(String filePath) {
        Preconditions.checkNotNull(filePath);
        int len = filePath.length();
        return len >= 254;
    }

    public static boolean isValidURL(URI uri) {
        if (null == uri) {
            return false;
        }
        try {
            URL url = uri.toURL();
        } catch (Exception ex) {
            return false;
        }
        return true;
    }

    public static String getPreviousStackTrace(int upper) {
        Throwable throwable = new Throwable();
        StackTraceElement[] stackTrace = throwable.getStackTrace();
        return stackTrace[(upper + 1) % stackTrace.length].toString();
    }

    public static String getDefaultUserAgent() {
        String os = System.getProperty("os.name");
        if (StringUtils.isNotBlank(os)) {
            if (os.startsWith("Windows")) {
                return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36";
            }
            if (os.startsWith("Mac")) {
                return "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36";
            }
            if (os.startsWith("Linux")) {
                return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36";
            }
        }
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36";
    }

    public static <T> String mapToStrIfNull(final T object, final String defaultValue) {
        return object != null ? object.toString() : defaultValue;
    }

    public static <S, T> T mapToDefaultIfNull(final S object, final Function<S, T> mapper, final T defaultValue) {
        return object != null ? mapper.apply(object) : defaultValue;
    }

    public static <S, T> T mapToNullable(final S object, final Function<S, T> mapper) {
        return object != null ? mapper.apply(object) : null;
    }

    public static BigDecimal rate(long a, long b) {
        return BigDecimal.valueOf(a).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(b), 3, RoundingMode.HALF_UP);
    }

    public static Path checkAndCreateDir(Path dir, String dirName) throws IOException {
        Preconditions.checkNotBlank(dirName);

        Preconditions.m3u8Check(Objects.nonNull(dir), "%s is null", dirName);
        if (Files.exists(dir)) {
            Preconditions.m3u8Check(Files.isDirectory(dir), "%s is not a directory: %s", dirName, dir);
        } else {
            Files.createDirectory(dir);
        }
        return dir;
    }

    public static void deleteRecursively(Path dir) throws IOException {
        Preconditions.checkNotNull(dir);
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @SuppressWarnings("unchecked")
    public static <T extends Throwable, R> R sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }

}

