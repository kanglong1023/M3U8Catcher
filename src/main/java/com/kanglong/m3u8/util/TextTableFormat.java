package com.kanglong.m3u8.util;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.Marker;

import java.io.Flushable;
import java.io.IOException;
import java.util.*;

import static com.kanglong.m3u8.util.AsciiFullWidthUtil.*;
import static com.kanglong.m3u8.util.Preconditions.checkNotEmpty;
import static com.kanglong.m3u8.util.Preconditions.checkNotNull;
import static com.kanglong.m3u8.util.TextTableFormat.Printer.*;

public final class TextTableFormat {

    private final char borderChar = '-';

    private final char fwSpace = FW_SPACE;

    private final char columnSeparator = '|';

    private final char fwBorderChar = hw2fw(borderChar);

    private final Printer printer;

    private final Formatter formatter;

    private final String halfPaddingData;

    private final String halfPaddingBorder;

    private final BitSet titlesFullWidthGate = new BitSet();

    private List<String> titles;

    private List<List<String>> data;

    public TextTableFormat() {
        this(console());
    }

    public TextTableFormat(Printer printer) {
        Preconditions.checkNotNull(printer);

        final int halfPaddingLength = 1;
        char[] hpd = new char[halfPaddingLength];
        char[] hpb = new char[halfPaddingLength];
        Arrays.fill(hpd, ' ');
        Arrays.fill(hpb, borderChar);

        this.printer = printer;
        this.halfPaddingData = new String(hpd);
        this.halfPaddingBorder = new String(hpb);
        this.formatter = new Formatter(new StringBuilder());
    }

    private void print(String s) {
        printer.print(s);
    }

    private void println(String s) {
        printer.println(s);
    }

    private String format(String format, Object... args) {
        StringBuilder out = (StringBuilder) formatter.format(format, args).out();
        String str = out.toString();
        out.setLength(0);
        return str;
    }

    private void printBorder(ColumnPrintAttr[] columnPrintAttrs) {
        StringBuilder row = new StringBuilder();
        for (ColumnPrintAttr columnPrintAttr : columnPrintAttrs) {
            row.append('+').append(halfPaddingBorder);

            int columnMaxLength = columnPrintAttr.columnMaxLength;
            int fullWidthMaxLength = columnPrintAttr.fullWidthMaxLength;

            if (fullWidthMaxLength > 0) {
                for (int i = 0; i < fullWidthMaxLength; i++) {
                    row.append(fwBorderChar);
                }
            }
            for (int j = 0; j < columnMaxLength; j++) {
                row.append(borderChar);
            }
            row.append(halfPaddingBorder);
        }
        row.append('+');
        println(row.toString());
    }

    private void printTitle(ColumnPrintAttr[] columnPrintAttrs) {
        int col = -1;
        StringBuilder row = new StringBuilder();
        List<String> titles = ListUtils.emptyIfNull(this.titles);
        for (String title : titles) {
            col++;
            title = defaultIfNull(title);

            ColumnPrintAttr columnPrintAttr = columnPrintAttrs[col];
            int columnMaxLength = columnPrintAttr.columnMaxLength;
            int fullWidthMaxLength = columnPrintAttr.fullWidthMaxLength;

            row.append(columnSeparator).append(halfPaddingData);
            if (fullWidthMaxLength > 0) {
                int fullWidthLength = columnPrintAttr.getRowFullWidthLength(0);
                int paddingFullWidthLength = fullWidthMaxLength - fullWidthLength;
                for (int i = 0; i < paddingFullWidthLength; i++) {
                    row.append(fwSpace);
                }
                columnMaxLength += fullWidthLength;
            }
            row.append(format("%" + columnMaxLength + "s", title)).append(halfPaddingData);
        }
        row.append(columnSeparator);
        println(row.toString());
    }

    private void printData(ColumnPrintAttr[] columnPrintAttrs) {
        if (CollectionUtils.isEmpty(data)) {
            return;
        }
        int rowNum = -1;
        StringBuilder row = new StringBuilder();
        for (List<String> r : data) {
            rowNum++;
            Iterator<String> iterator = r.iterator();
            for (ColumnPrintAttr columnPrintAttr : columnPrintAttrs) {
                String s = defaultIfNull(iterator.hasNext() ? iterator.next() : null);

                int columnMaxLength = columnPrintAttr.columnMaxLength;
                int fullWidthMaxLength = columnPrintAttr.fullWidthMaxLength;

                row.append(columnSeparator).append(halfPaddingData);
                if (fullWidthMaxLength > 0) {
                    int fullWidthLength = columnPrintAttr.getRowFullWidthLength(rowNum + 1);
                    int paddingFullWidthLength = fullWidthMaxLength - fullWidthLength;
                    for (int i = 0; i < paddingFullWidthLength; i++) {
                        row.append(fwSpace);
                    }
                    columnMaxLength += fullWidthLength;
                }
                row.append(format("%" + columnMaxLength + "s", s)).append(halfPaddingData);
            }
            row.append(columnSeparator);
            println(row.toString());
            row.setLength(0);
        }
    }

    private ColumnPrintAttr[] getColumnMaxLengths() {
        if (CollectionUtils.isEmpty(titles)) {
            return new ColumnPrintAttr[0];
        }
        List<List<String>> rows = ListUtils.emptyIfNull(data);

        int columnSize = titles.size();
        BitSet titlesFullWidthGate = this.titlesFullWidthGate;
        ColumnPrintAttr[] columnPrintAttrs = new ColumnPrintAttr[columnSize];

        int col = -1;
        for (String title : titles) {
            col++;
            title = defaultIfNull(title);

            ColumnPrintAttr columnPrintAttr = new ColumnPrintAttr();
            columnPrintAttrs[col] = columnPrintAttr;

            int titleLength = title.length();

            if (titlesFullWidthGate.get(col)) {
                int titleFullWidthLength = getFullWidthLength(title);

                if (titleFullWidthLength > 0) {
                    columnPrintAttr.addFullWidthLength(0, titleFullWidthLength);
                }
                columnPrintAttr.fullWidthMaxLength = titleFullWidthLength;
                columnPrintAttr.columnMaxLength = titleLength - titleFullWidthLength;
            } else {
                columnPrintAttr.columnMaxLength = titleLength;
            }
        }

        int rowNum = -1;
        for (List<String> row : rows) {
            rowNum++;
            Iterator<String> iterator = row.iterator();
            for (int c = 0; c < columnPrintAttrs.length; c++) {
                if (!iterator.hasNext()) {
                    break;
                }
                ColumnPrintAttr columnPrintAttr = columnPrintAttrs[c];
                String s = defaultIfNull(iterator.next());
                int length = s.length();

                if (titlesFullWidthGate.get(c)) {
                    int fullWidthLength = getFullWidthLength(s);
                    int halfWidthLength = length - fullWidthLength;

                    if (fullWidthLength > 0) {
                        columnPrintAttr.addFullWidthLength(rowNum + 1, fullWidthLength);
                    }
                    columnPrintAttr.columnMaxLength = Math.max(columnPrintAttr.columnMaxLength, halfWidthLength);
                    columnPrintAttr.fullWidthMaxLength = Math.max(columnPrintAttr.fullWidthMaxLength, fullWidthLength);
                } else {
                    columnPrintAttr.columnMaxLength = Math.max(columnPrintAttr.columnMaxLength, length);
                }
            }
        }

        return columnPrintAttrs;
    }

    private int getFullWidthLength(String s) {
        if (null == s || 0 == s.length()) {
            return 0;
        }
        int len = 0;
        for (char c : s.toCharArray()) {
            if (isFullWidthChar(c)) {
                len++;
            }
        }
        return len;
    }

    // simplify, not an ascii character
    public boolean isFullWidthChar(char ch) {
        return !isAsciiPrintable(ch);
    }

    private String defaultIfNull(String str) {
        return ObjectUtils.defaultIfNull(str, "NULL");
    }

    public TextTableFormat setTitles(String... titles) {
        return setTitles(Arrays.asList(checkNotNull(titles)));
    }

    public TextTableFormat setTitles(List<String> titles) {
        if (CollectionUtils.isNotEmpty(this.titles)) {
            this.titlesFullWidthGate.clear();
        }
        this.titles = (List<String>) checkNotEmpty(titles);
        return this;
    }

    public TextTableFormat setFullWidthGate(int idx) {
        return setFullWidthGate(idx, true);
    }

    public TextTableFormat setFullWidthGate(int idx, boolean gate) {
        this.titlesFullWidthGate.set(idx, gate);
        return this;
    }

    public TextTableFormat setData(List<List<String>> data) {
        this.data = (List<List<String>>) checkNotEmpty(data);
        return this;
    }

    public TextTableFormat addData(String... row) {
        return addData(Arrays.asList(checkNotNull(row)));
    }

    public TextTableFormat addData(List<String> row) {
        checkNotEmpty(row);
        List<List<String>> data = this.data;
        if (null == data) {
            this.data = data = CollUtil.newArrayList();
        }
        data.add(row);
        return this;
    }

    public int getTitleSize() {
        return CollectionUtils.isEmpty(this.titles) ? 0 : this.titles.size();
    }

    public void print() {
        Preconditions.checkNotNull(this.printer, "printer is null");
        if (CollectionUtils.isEmpty(titles)) {
            println("empty titles");
            return;
        }

        ColumnPrintAttr[] columnPrintAttrs = getColumnMaxLengths();

        printBorder(columnPrintAttrs);

        printTitle(columnPrintAttrs);

        printBorder(columnPrintAttrs);

        printData(columnPrintAttrs);

        printBorder(columnPrintAttrs);

    }

    public boolean hasData() {
        return CollectionUtils.isNotEmpty(this.data);
    }

    public void clearData() {
        Printer printer = this.printer;
        if (null != printer) {
            printer.clear();
        }
        List<List<String>> data = this.data;
        if (CollectionUtils.isNotEmpty(data)) {
            data.clear();
        }
    }

    public static TextTableFormat textTableFormat(Logger logger) {
        return textTableFormat(logger, null);
    }

    public static TextTableFormat textTableFormat(Appendable appendable) {
        return new TextTableFormat(buffer(appendable));
    }

    public static TextTableFormat textTableFormat(Logger logger, Marker marker) {
        return new TextTableFormat(logPrinter(logger, marker));
    }

    private static class ColumnPrintAttr {

        int columnMaxLength;

        int fullWidthMaxLength;

        private List<FullWidthValue> fullWidthLength;

        void addFullWidthLength(int rowNum, int length) {
            if (null == fullWidthLength) {
                fullWidthLength = CollUtil.newArrayList();
            }
            FullWidthValue value = new FullWidthValue();
            value.rowNum = rowNum;
            value.length = length;
            fullWidthLength.add(value);
        }

        int getRowFullWidthLength(int rowNum) {
            if (CollectionUtils.isEmpty(fullWidthLength)) {
                return 0;
            }
            for (FullWidthValue fullWidthValue : fullWidthLength) {
                if (fullWidthValue.rowNum == rowNum) {
                    return fullWidthValue.length;
                }
            }
            return 0;
        }

        private static class FullWidthValue {

            int rowNum;

            int length;
        }

    }

    public interface Printer {

        void print(String s);

        void println(String s);

        default void clear() {
        }

        static Printer console() {
            return new Printer() {

                @Override
                public void print(String s) {
                    System.out.print(s);
                }

                @Override
                public void println(String s) {
                    System.out.println(s);
                }

            };
        }

        static Printer logPrinter(Logger logger, Marker marker) {
            Preconditions.checkNotNull(logger);
            return new Printer() {

                @Override
                public void print(String s) {
                    logger.info(marker, s);
                }

                @Override
                public void println(String s) {
                    logger.info(marker, "{}", s);
                }
            };
        }

        static Printer buffer(Appendable appendable) {
            Preconditions.checkNotNull(appendable);
            return new Printer() {

                @Override
                public void print(String s) {
                    try {
                        appendable.append(s);
                    } catch (Exception ex) {
                        Utils.sneakyThrow(ex);
                    }
                }

                @Override
                public void println(String s) {
                    try {
                        appendable.append(s).append("\n");
                    } catch (Exception ex) {
                        Utils.sneakyThrow(ex);
                    }
                }

                @Override
                public void clear() {
                    if (appendable instanceof Flushable) {
                        try {
                            ((Flushable) appendable).flush();
                        } catch (IOException e) {
                            Utils.sneakyThrow(e);
                        }
                    }
                    if (appendable instanceof StringBuilder) {
                        ((StringBuilder) appendable).setLength(0);
                    }
                    if (appendable instanceof StringBuffer) {
                        ((StringBuffer) appendable).setLength(0);
                    }
                }
            };
        }
    }

}
