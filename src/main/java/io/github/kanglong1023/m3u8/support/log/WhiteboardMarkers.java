package io.github.kanglong1023.m3u8.support.log;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.Arrays;
import java.util.List;

public final class WhiteboardMarkers {

    private WhiteboardMarkers() {
    }

    public static List<String> getWhiteboardMarkerStr() {
        return Arrays.asList("WHITEBOARD", "STD", "PROCESS_STD");
    }

    public static Marker getWhiteboardMarker() {
        return MarkerFactory.getMarker("WHITEBOARD");
    }

}
