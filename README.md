# M3U8Catcher

![](https://img.shields.io/badge/build-passing-brightgreen)
![GitHub](https://img.shields.io/github/license/kanglong1023/M3U8Catcher)
![](https://img.shields.io/badge/m3u8-downloader-orange)
[![](https://img.shields.io/github/v/release/kanglong1023/M3U8Catcher)](https://github.com/kanglong1023/M3U8Catcher/releases)
[![jdk version](https://img.shields.io/badge/jdk-1.8+-blue.svg)](https://docs.oracle.com/javase/8/docs/api)
[![publish](https://img.shields.io/badge/publish-mvn--central-brightgreen)](https://central.sonatype.com/artifact/io.github.kanglong1023/m3u8-catcher)
![](https://img.shields.io/github/last-commit/kanglong1023/M3U8Catcher)

A simple, high-performance m3u8 downloader suitable for batch task processing

![效果图](https://kanglong1023.github.io/source/M3U8Catcher/images/m3u8_catcher_example.gif)

## README [中文](README.zh-CN.md)

## Table of Contents

- [Background](#background)
- [Feature](#feature)
- [Install](#install)
- [Usage](#usage)
    - [Quickstart](#quickstart)
    - [Customization](#customization)
- [Maintainers](#maintainers)
- [Contributing](#contributing)
- [License](#license)

## Background

There was a requirement to download a large number of m3u8 live classes some time ago. Since we need to integrate external tools to obtain specific urls, we hope to have an independent m3u8 download module. Unsatisfied with the existing tools, I decided to create a m3u8 downloader.

## Feature

- Based on java 8, mainly compatible with hls 3（#EXT-X-VERSION:3）
- Support Master List（#EXT-X-STREAM-INF）
- Support AES-128-CBC decryption,Supported key labels:#EXT-X-KEY和#EXT-X-SESSION-KEY
- Support batch m3u8 concurrent download
- Support http2, support custom request header, connection timeout and max concurrent connection (maxConnPerRoute)
- Support retry, custom retry times and retry interval
- Support custom http proxy, use system proxy by default
- Support breakpoint resuming, support caching intermediate results, after download is complete, the cache would be clear by default
- Support to use ffmpeg to merge into mp4, or just do binary merge
- Automatically import ffmpeg compatible with all platforms, so there is no need to install ffmpeg
- Full use of nio, higher resource utilization when batch downloading
- Support file asynchronous sink, asynchronous decryption, then reduce the blocking of io thread
- Support buffer pool to reduce memory allocation and gc times, the customized buffer pool also can lower consumption of synchronization; using direct buffer to decrease memory copy

## Install

```xml

<dependency>
    <groupId>io.github.kanglong1023</groupId>
    <artifactId>m3u8-catcher</artifactId>
    <version>1.0.0</version>
</dependency>
```

or use

```groovy
dependencies {
    implementation 'io.github.kanglong1023:m3u8-catcher:1.0.0'
}
```

This could import ffmpeg for all platform. If you don't need platform compatibility, you can also import ffmpeg for a given system like this:

```xml

<project>
    ...
    <properties>
        <ffmpeg.version>5.0</ffmpeg.version>
        <bytedeco.version>1.5.7</bytedeco.version>
        <bytedeco.cur.platform>macosx-x86_64</bytedeco.cur.platform>
        <bytedeco-ffmpeg.version>${ffmpeg.version}-${bytedeco.version}</bytedeco-ffmpeg.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>io.github.kanglong1023</groupId>
            <artifactId>m3u8-catcher</artifactId>
            <version>1.0.0</version>
            <exclusions>
                <exclusion>
                    <groupId>org.bytedeco</groupId>
                    <artifactId>ffmpeg-platform</artifactId>
                </exclusion>
            </exclusions>
        </dependency>

        <dependency>
            <groupId>org.bytedeco</groupId>
            <artifactId>javacpp</artifactId>
            <version>${bytedeco.version}</version>
        </dependency>
        <dependency>
            <groupId>org.bytedeco</groupId>
            <artifactId>ffmpeg</artifactId>
            <version>${bytedeco-ffmpeg.version}</version>
        </dependency>
        <dependency>
            <groupId>org.bytedeco</groupId>
            <artifactId>javacpp</artifactId>
            <version>${bytedeco.version}</version>
            <classifier>${bytedeco.cur.platform}</classifier>
        </dependency>
        <dependency>
            <groupId>org.bytedeco</groupId>
            <artifactId>ffmpeg</artifactId>
            <version>${bytedeco-ffmpeg.version}</version>
            <classifier>${bytedeco.cur.platform}</classifier>
        </dependency>
    </dependencies>
    ...
</project>
```

Specify the platform through the property: `bytedeco.cur.platform`; Attribute value can be obtained from the pom file: `ffmpeg-platform`, for example:

* android-arm
* android-arm64
* android-x86
* android-x86_64
* linux-armhf
* linux-arm64
* linux-ppc64le
* linux-x86
* linux-x86_64
* macosx-arm64
* macosx-x86_64
* windows-x86
* windows-x86_64

It can be determined by system properties: `os.name` and `os.arch`.

Alternatively, You can exclude ffmpeg dependencies if ffmpeg is installed on your local environment: 

```xml

<dependency>
    <groupId>io.github.kanglong1023</groupId>
    <artifactId>m3u8-catcher</artifactId>
    <version>1.0.0</version>
    <exclusions>
        <exclusion>
            <groupId>org.bytedeco</groupId>
            <artifactId>ffmpeg-platform</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

The program finally use local ffmpeg to merge the videos.

If you exclude dependencies and ffmpeg is not installed on your local environment, try binary merge (`M3u8DownloadBuilder.mergeWithoutConvertToMp4())`). Most players support ts files.

## Usage

```java
public class Main {

    public static void main(String[] args) {
        // download one, use the file name (***.mp4) in the url, save it in the execution directory After the download is complete
        M3u8Downloads.downloadOne("https://host/path/***.m3u8");

        // download series
        M3u8Downloads.downloadSeriesFromUrl("https://host/path/***1.m3u8", "https://host/path/***2.m3u8");
    }
}
```

### Quickstart

Plain download tasks can use the api of M3u8Downloads, just like:

```java
import io.github.kanglong1023.m3u8.M3u8Downloads.M3u8HttpHeader;
import io.github.kanglong1023.m3u8.core.M3u8Download;
import io.github.kanglong1023.m3u8.M3u8Downloads;

public class Main {

    public static void main(String[] args) {
        String fileName = "video.mp4";
        String saveDir = "/Users/kanglong1023/vd";
        String url = "https://host/path/playlist.m3u8";

        // 1.download one
        M3u8Downloads.downloadOne(url, fileName, saveDir);

        // 2.carry cookie, the cookie will only be carried when requesting m3u8 content by default
        String cookieStr = "****";
        downloadOneCarryCookie(url, fileName, saveDir, cookieStr);

        // 3.custom http headers
        downloadOne(url, fileName, saveDir,
                M3u8HttpHeader.as("Accept", "*/*", null),
                M3u8HttpHeader.as("Cache-Control", "no-cache", null)
        );

        // 4.download series
        String fileName2 = "video2.mp4";
        String url2 = "https://host/path/playlist2.m3u8";
        downloadSeriesInUnitedDir(url, fileName, url2, fileName2, saveDir);

        // 5.customizing download parameters
        M3u8Download m3u8Download = M3u8Download.builder()
                .setUri(url)
                .setFileName(fileName)
                .setTargetFiletDir(saveDir)
                .forceCacheAssignmentBasedOnFileName()
                .build();
        M3u8Download m3u8Download2 = newDownload(url2, fileName2, saveDir,
                M3u8HttpHeader.as("Accept", "*/*", null),
                M3u8HttpHeader.as("Cache-Control", "no-cache", null)
        );
        download(m3u8Download, m3u8Download2);

        // For more APIs, you can read the source code of the M3u8Downloads
    }
}
```

### Customization

Customized parameters are mainly concentrated in these three classes: HttpRequestManagerConfig, TsDownloadOptionsSelector and M3u8DownloadBuilder. The first two are used to create M3u8Executor and the last one create M3u8Download.

1. HttpRequestManagerConfig: Using builder Pattern, commonly used parameters are: **maxConnPerRoute** (maximum number of connections per site) and **overrideSystemProxy** (close system proxy)

2. TsDownloadOptionsSelector: It is used to dynamically config download parameters: whether to asynchronously write to disk, whether to use buffer pool, if there are many download tasks, you can use the default implementation.

3. M3u8DownloadBuilder: **optionsForApplyTsCache** is used to config the verification strategy of ts cache, **START_OVER** means to ignore and delete the cache and re-download, **FORCE_APPLY_CACHE_BASED_ON_FILENAME** means to match the cache according to the file name, **SANITY_CHECK** is to strictly verify whether the url matches; if **mergeWithoutConvertToMp4** is true, ffmpeg will not be used to convert to mp4

> The main object to execute the download task is M3u8Executor, you can use the M3u8Downloads api, or follow the method of M3u8Downloads to create M3u8Executor, and then submit the M3u8Download task.

```java

import io.github.kanglong1023.m3u8.M3u8Downloads;
import io.github.kanglong1023.m3u8.core.M3u8Download;
import io.github.kanglong1023.m3u8.core.TsDownloadOptionsSelector.PlainTsDownloadOptionsSelector;
import io.github.kanglong1023.m3u8.http.config.HttpRequestManagerConfig;

import java.util.ArrayList;

public class Main {

    public static void main(String[] args) {

        HttpRequestManagerConfig managerConfig = HttpRequestManagerConfig.custom()
                .maxConnPerRoute(10)
                .overrideSystemProxy()
                .build();

        PlainTsDownloadOptionsSelector optionsSelector = PlainTsDownloadOptionsSelector.optionsSelector(true, true);

        String fileName = "video.mp4";
        String fileName2 = "video.mp4";
        String saveDir = "/Users/kanglong1023/vd";
        String url = "https://host/path/playlist.m3u8";
        String url2 = "https://host/path/playlist.m3u8";

        M3u8Download m3u8Download = M3u8Download.builder()
                .setUri(url)
                .setFileName(fileName)
                .setTargetFiletDir(saveDir)
                .mergeWithoutConvertToMp4()
                .forceCacheAssignmentBasedOnFileName()
                .build();
        M3u8Download m3u8Download2 = newDownload(url2, fileName2, saveDir,
                M3u8HttpHeader.as("Accept", "*/*", null),
                M3u8HttpHeader.as("Cache-Control", "no-cache", null)
        );

        List<M3u8Download> downloadList = new ArrayList<>();

        downloadList.add(m3u8Download);

        downloadList.add(m3u8Download2);

        M3u8Downloads.download(managerConfig, optionsSelector, downloadList);

    }

}


```

## Maintainers

[@kanglong1023](https://github.com/kanglong1023).

## Contributing

Feel free to dive in! [Open an issue](https://github.com/kanglong1023/M3U8Catcher/issues/new) or submit PRs.

## License

[MIT](https://github.com/kanglong1023/M3U8Catcher/blob/main/LICENSE) © kanglong1023
