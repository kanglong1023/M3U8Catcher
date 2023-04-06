package com.kanglong.m3u8.util;

import com.kanglong.m3u8.support.log.WhiteboardMarkers;
import org.slf4j.Marker;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class VideoUtil {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(VideoUtil.class);

    private static class FfmpegPathHolder {

        private static final boolean useLocalFfmpeg = Boolean.getBoolean("com.kanglong.m3u8.util.VideoUtil.useLocalFfmpeg");

        private static final String ffmpegPath = loadFfmpegPath();

    }

    private VideoUtil() {
    }

    private static String loadFfmpegPath() {
        if (FfmpegPathHolder.useLocalFfmpeg) {
            if (execCommand(Arrays.asList("ffmpeg", "-version"))) {
                return "ffmpeg";
            }
            throw new RuntimeException("use Local ffmpeg, can not find ffmpeg tool");
        }

        try {
            // try load from lib
            Class<?> ffmpegClazz = Class.forName("org.bytedeco.ffmpeg.ffmpeg");
            Class<?> loaderClazz = Class.forName("org.bytedeco.javacpp.Loader");
            // Loader.load(org.bytedeco.ffmpeg.ffmpeg.class);
            Method loadMethod = loaderClazz.getDeclaredMethod("load", Class.class);
            Object result = loadMethod.invoke(loaderClazz, ffmpegClazz);
            if (result instanceof String) {
                return ((String) result);
            }
        } catch (ClassNotFoundException e) {
            // try use ffmpeg from env:PATH
            if (execCommand(Arrays.asList("ffmpeg", "-version"))) {
                return "ffmpeg";
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        throw new RuntimeException("can not find ffmpeg tool");
    }

    public static boolean convertToMp4(Path destVideoPath, Path... sourceVideoPaths) {
        return convertToMp4(destVideoPath, Arrays.asList(sourceVideoPaths));
    }

    public static boolean convertToMp4(Path destVideoPath, List<Path> sourceVideoPaths) {
        String ffmpegPath = FfmpegPathHolder.ffmpegPath;

        Preconditions.checkNotBlank(ffmpegPath, "ffmpeg path");
        Preconditions.checkNotEmpty(sourceVideoPaths);
        Preconditions.checkArgument(destVideoPath.isAbsolute());
        Preconditions.checkArgument(Files.notExists(destVideoPath));

        long startTime = System.currentTimeMillis();
        log.info("convert to ({}) start", destVideoPath.getFileName());

        Path allTsFile;
        if (sourceVideoPaths.size() > 1) {

            allTsFile = sourceVideoPaths.get(0).resolveSibling("all.ts");
            Path listFile = sourceVideoPaths.get(0).resolveSibling("list.txt");

            List<String> contents = CollUtil.newArrayListWithCapacity(sourceVideoPaths.size());
            for (Path path : sourceVideoPaths) {
                String content = String.format("file '%s'", path.toString());
                contents.add(content);
            }
            try {
                Files.deleteIfExists(listFile);
                Files.deleteIfExists(allTsFile);
                Files.write(listFile, contents, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }

            List<String> command = new ArrayList<>();
            command.add(ffmpegPath);

            command.add("-protocol_whitelist");
            command.add("concat,file,http,https,tcp,tls,crypto");

            command.add("-f");
            command.add("concat");

            command.add("-safe");
            command.add("0");

            command.add("-i");
            command.add(listFile.toString());

            command.add("-c");
            command.add("copy");

            command.add(allTsFile.toString());

            boolean res = execCommand(command);
            if (!res) {
                log.error("convert failed when concat to {}", allTsFile.getFileName());
                return false;
            }

        } else {
            allTsFile = sourceVideoPaths.get(0);
        }

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);

        command.add("-i");
        command.add(allTsFile.toString());

        command.add("-acodec");
        command.add("copy");

        command.add("-vcodec");
        command.add("copy");

        command.add(destVideoPath.toString());

        try {
            boolean res = execCommand(command);
            if (res) {
                log.info("convert succeed");
            } else {
                log.error("convert failed");
            }

            Files.deleteIfExists(allTsFile);
            return res;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return false;
        } finally {
            long endTime = System.currentTimeMillis();
            log.info("convert cost {} seconds", (endTime - startTime) / 1000.0);
        }
    }

    private static boolean execCommand(List<String> commands) {
        log.info("execCommand {}", String.join(" ", commands));
        try {
            Process videoProcess = new ProcessBuilder(commands)
                    .redirectErrorStream(true)
                    .start();
            String s;
            Marker processStd = WhiteboardMarkers.getWhiteboardMarker();
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(videoProcess.getInputStream()));
            while ((s = stdInput.readLine()) != null) {
                log.warn(processStd, s);
            }

            int code = videoProcess.waitFor();
            log.info("execCommand code={}", code);

            return code == 0;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return false;
        }
    }

}
