# M3U8Catcher

---

![](https://img.shields.io/badge/build-passing-brightgreen)
![GitHub](https://img.shields.io/github/license/kanglong1023/M3U8Catcher)
![](https://img.shields.io/badge/m3u8-downloader-orange)
[![](https://img.shields.io/github/v/release/kanglong1023/M3U8Catcher)](https://github.com/kanglong1023/M3U8Catcher/releases)
[![jdk version](https://img.shields.io/badge/jdk-1.8+-blue.svg)](https://docs.oracle.com/javase/8/docs/api)
[![publish](https://img.shields.io/badge/publish-mvn--central-brightgreen)](https://central.sonatype.com/artifact/io.github.kanglong1023/m3u8-catcher)
![](https://img.shields.io/github/last-commit/kanglong1023/M3U8Catcher)

> README [English](README.md) | 中文

一个简单、高性能、适用于批量任务处理的m3u8资源下载器

![效果图](https://kanglong1023.github.io/source/M3U8Catcher/images/m3u8_catcher_example.gif)

## 目录

- [背景](#背景)
- [功能特性](#功能特性)
- [安装](#安装)
- [使用](#使用)
    - [Quickstart](#quickstart)
    - [自定义参数](#自定义参数)
- [维护者](#维护者)
- [如何贡献](#如何贡献)
- [使用许可](#使用许可)

## 背景

之前有个下载大量m3u8直播课程的需求，由于获取具体url需集成外部工具，所以期望一个独立的m3u8下载模块，网上找的不是很满意，于是就想自己实现了。

## 功能特性

- 基于Java 8, 主要兼容version 3（#EXT-X-VERSION:3）
- 支持Master List（#EXT-X-STREAM-INF）
- 支持AES-128-CBC解密，支持的密钥标签:#EXT-X-KEY和#EXT-X-SESSION-KEY
- 支持批量m3u8并发下载
- 支持http2，支持自定义请求头，自定义连接超时，自定义连接并发数（maxConnPerRoute）
- 支持重试，自定义重试次数、重试间隔
- 支持自定义http代理，默认自动使用系统代理
- 支持断点续传，支持缓存中间结果，默认完成后自动删除缓存
- 支持使用ffmpeg合并成mp4，也可以仅做二进制合并
- 自动引入全平台兼容的ffmpeg，本机可以无需安装ffmpeg
- 全面采用nio，批量下载时资源利用率更高
- 支持文件异步刷盘、异步解密，减少io线程的阻塞
- 支持内存对象池，减少内存分配和gc次数，定制化的对象池可以降低同步损耗；使用直接内存，减少内存拷贝

## 安装

```xml

<dependency>
    <groupId>io.github.kanglong1023</groupId>
    <artifactId>m3u8-catcher</artifactId>
    <version>1.0</version>
</dependency>
```

或者

```groovy
dependencies {
    implementation 'io.github.kanglong1023:m3u8-catcher:1.0'
}
```

默认引入全平台的ffmpeg，如果不需要兼容全平台，也可以只引入指定系统的ffmpeg，如：

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
            <version>1.0</version>
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

通过属性`bytedeco.cur.platform`来指定所在平台，具体的属性值可以查看`ffmpeg-platform`的pom文件，比如有：

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

可以通过系统属性`os.name`和`os.arch`来判断。

另外，如果本机已安装ffmpeg，也可直接忽略引入ffmpeg依赖：

```xml

<dependency>
    <groupId>io.github.kanglong1023</groupId>
    <artifactId>m3u8-catcher</artifactId>
    <version>1.0</version>
    <exclusions>
        <exclusion>
            <groupId>org.bytedeco</groupId>
            <artifactId>ffmpeg-platform</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

程序最终会使用本机的ffmpeg合并视频。

不引入ffmpeg依赖且本机未安装ffmpeg时，可以选择使用二进制合并（`M3u8DownloadBuilder.mergeWithoutConvertToMp4()`），大多数播放器支持ts格式的视频。

## 使用

```java
public class Main {

    public static void main(String[] args) {
        // 下载单个，使用url中的文件名（***.mp4），下载成功后，保存在程序执行目录
        M3u8Downloads.downloadOne("https://host/path/***.m3u8");

        // 下载多个
        M3u8Downloads.downloadSeriesFromUrl("https://host/path/***1.m3u8", "https://host/path/***2.m3u8");
    }
}
```

### Quickstart

简单的下载任务直接使用M3u8Downloads的api，如：

```java
import io.github.kanglong1023.m3u8.M3u8Downloads.M3u8HttpHeader;
import io.github.kanglong1023.m3u8.core.M3u8Download;
import io.github.kanglong1023.m3u8.M3u8Downloads;

public class Main {

    public static void main(String[] args) {
        String fileName = "video.mp4";
        String saveDir = "/Users/kanglong1023/vd";
        String url = "https://host/path/playlist.m3u8";

        // 1.下载单个视频
        M3u8Downloads.downloadOne(url, fileName, saveDir);

        // 2.带cookie，cookie默认只会在请求m3u8内容时携带
        String cookieStr = "****";
        downloadOneCarryCookie(url, fileName, saveDir, cookieStr);

        // 3.自定义请求头
        downloadOne(url, fileName, saveDir,
                M3u8HttpHeader.as("Accept", "*/*", null),
                M3u8HttpHeader.as("Cache-Control", "no-cache", null)
        );

        // 4.下载多个视频
        String fileName2 = "video2.mp4";
        String url2 = "https://host/path/playlist2.m3u8";
        downloadSeriesInUnitedDir(url, fileName, url2, fileName2, saveDir);

        // 5.定制下载参数的场景
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

        // 更多的api可以直接查看M3u8Downloads类源码
    }
}
```

### 自定义参数

自定义参数主要集中在这三个类：HttpRequestManagerConfig、TsDownloadOptionsSelector和M3u8DownloadBuilder，前两个用于创建M3u8Executor，后者创建M3u8Download。

1. HttpRequestManagerConfig：使用builder模式，常用的参数有：maxConnPerRoute（每个站点最大连接数）和overrideSystemProxy（关闭系统代理）

2. TsDownloadOptionsSelector：用于动态设置下载参数：是否异步写入磁盘，是否使用buffer pool，如果下载任务较多，可以直接使用默认的实现。

3. M3u8DownloadBuilder：optionsForApplyTsCache用于设置ts缓存的校验策略，START_OVER表示忽略并删除缓存重新下载，FORCE_APPLY_CACHE_BASED_ON_FILENAME表示直接根据文件名匹配缓存，SANITY_CHECK是严格校验url是否匹配；mergeWithoutConvertToMp4为true则不使用ffmpeg转成mp4

> 执行下载任务的主体是M3u8Executor，可以直接使用M3u8Downloads的api，也可以仿照M3u8Downloads方法，创建M3u8Executor，然后提交M3u8Download任务。

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

## 维护者

[@kanglong1023](https://github.com/kanglong1023).

## 如何贡献

非常欢迎你的加入！[提一个 Issue ](https://github.com/kanglong1023/M3U8Catcher/issues/new) 或者提交一个 Pull Request。

## 使用许可

[MIT](https://github.com/kanglong1023/M3U8Catcher/blob/main/LICENSE) © kanglong1023
