package com.kanglong.m3u8.support.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase;
import com.kanglong.m3u8.util.CollUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.*;

import static ch.qos.logback.core.pattern.color.ANSIConstants.*;

public class ColorConverterWithMarkerWhiteboard extends ForegroundCompositeConverterBase<ILoggingEvent> {

    private static final Map<Integer, String> LEVELS;

    private static final Map<String, String> ELEMENTS;

    static {
        Map<String, String> ansiElements = new HashMap<>();
        ansiElements.put("red", RED_FG);
        ansiElements.put("cyan", CYAN_FG);
        ansiElements.put("blue", BLUE_FG);
        ansiElements.put("green", GREEN_FG);
        ansiElements.put("yellow", YELLOW_FG);
        ansiElements.put("magenta", MAGENTA_FG);
        ansiElements.put("boldRed", BOLD + RED_FG);
        ansiElements.put("boldCyan", BOLD + CYAN_FG);
        ansiElements.put("boldBlue", BOLD + BLUE_FG);
        ansiElements.put("boldGreen", BOLD + GREEN_FG);
        ansiElements.put("boldYellow", BOLD + YELLOW_FG);
        ansiElements.put("boldMagenta", BOLD + MAGENTA_FG);
        ELEMENTS = Collections.unmodifiableMap(ansiElements);

        Map<Integer, String> ansiLevels = new HashMap<>();
        ansiLevels.put(Level.ERROR_INTEGER, RED_FG);
        ansiLevels.put(Level.INFO_INTEGER, CYAN_FG);
        ansiLevels.put(Level.WARN_INTEGER, BOLD + YELLOW_FG);
        LEVELS = Collections.unmodifiableMap(ansiLevels);
    }

    private PatternLayout whiteboardLayout;

    private final List<Marker> whiteboardMarkers = CollUtil.newArrayList();

    @Override
    public void start() {
        // spec marker
        CollectionUtils.emptyIfNull(WhiteboardMarkers.getWhiteboardMarkerStr()).stream()
                .filter(StringUtils::isNotBlank)
                .forEach(this::addWhiteboardMarker);

        PatternLayout patternLayout = new PatternLayout();
        patternLayout.setContext(getContext());
        patternLayout.setPattern("%m%n");
        patternLayout.start();
        this.whiteboardLayout = patternLayout;
        addInfo("ColorConverterWithMarkerWhiteboard start whiteboardLayout");
        super.start();
    }

    @Override
    public String convert(ILoggingEvent event) {
        Marker marker = event.getMarker();
        PatternLayout whiteboardLayout = this.whiteboardLayout;
        if (Objects.nonNull(marker) && Objects.nonNull(whiteboardLayout)) {
            for (Marker whiteboardMarker : this.whiteboardMarkers) {
                if (marker.contains(whiteboardMarker)) {
                    String intermediary = whiteboardLayout.doLayout(event);
                    return transform(event, intermediary);
                }
            }
        }
        return super.convert(event);
    }

    @Override
    protected String getForegroundColorCode(ILoggingEvent event) {
        String element = ELEMENTS.get(getFirstOption());
        if (element == null) {
            element = LEVELS.get(event.getLevel().toInteger());
            element = (element != null) ? element : DEFAULT_FG;
        }
        return element;
    }

    public void addWhiteboardMarker(String whiteboardMarkerStr) {
        if (StringUtils.isNotBlank(whiteboardMarkerStr)) {
            Marker whiteboardMarker = MarkerFactory.getMarker(whiteboardMarkerStr);
            this.whiteboardMarkers.add(whiteboardMarker);
            addInfo("ColorConverterWithMarkerWhiteboard add whiteboardMarker: " + whiteboardMarkerStr);
        }
    }

}
