package com.kanglong.m3u8.util;

/**
 * FW: full-width
 * HF: half-width
 * <p/>
 * ASCII space 32 => 12288
 * ASCII [33,126] => unicode [65281,65374]
 */
public final class AsciiFullWidthUtil {

    private AsciiFullWidthUtil() {
    }

    private static final char HW_SPACE = 32;

    private static final char ASCII_END = 126;

    private static final char ASCII_START = 33;

    public static final char FW_SPACE = 12288;

    private static final char HW2FW_STEP = 65248;

    private static final char UNICODE_END = 65374;

    private static final char UNICODE_START = 65281;

    public static char fw2hw(char ch) {
        if (ch == FW_SPACE) {
            return HW_SPACE;
        }

        if (ch >= UNICODE_START && ch <= UNICODE_END) {
            return (char) (ch - HW2FW_STEP);
        }

        return ch;
    }

    public static String fw2hw(String str) {
        if (str == null) {
            return null;
        }
        char[] c = str.toCharArray();
        for (int i = 0; i < c.length; ++i) {
            c[i] = fw2hw(c[i]);
        }
        return new String(c);
    }

    public static char hw2fw(char ch) {
        if (ch == HW_SPACE) {
            return FW_SPACE;
        }
        if (ch >= ASCII_START && ch <= ASCII_END) {
            return (char) (ch + HW2FW_STEP);
        }
        return ch;
    }

    public static String hw2fw(String src) {
        if (src == null) {
            return null;
        }

        char[] c = src.toCharArray();
        for (int i = 0; i < c.length; ++i) {
            c[i] = hw2fw(c[i]);
        }

        return new String(c);
    }

    public static boolean isAsciiPrintable(char ch) {
        return ch >= 32 && ch < 127;
    }

}
