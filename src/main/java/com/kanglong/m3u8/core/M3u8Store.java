package com.kanglong.m3u8.core;

import com.kanglong.m3u8.util.Preconditions;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

@Getter
@ToString
public class M3u8Store {

    private final URI m3u8Uri;

    private final URI finalM3u8Uri;

    private final URI masterM3u8Uri;

    private final String finalM3u8Content;

    private final String masterM3u8Content;

    public M3u8Store(URI m3u8Uri,
                     URI finalM3u8Uri, URI masterM3u8Uri,
                     String finalM3u8Content, String masterM3u8Content) {
        this.m3u8Uri = m3u8Uri;
        this.finalM3u8Uri = finalM3u8Uri;
        this.masterM3u8Uri = masterM3u8Uri;
        this.finalM3u8Content = finalM3u8Content;
        this.masterM3u8Content = masterM3u8Content;
    }

    public static M3u8Store load(Path m3u8StorePath) throws IOException {
        Preconditions.checkNotNull(m3u8StorePath);
        Preconditions.checkArgument(Files.isRegularFile(m3u8StorePath));

        Properties properties = new Properties();
        try(InputStream inputStream = Files.newInputStream(m3u8StorePath)) {
            properties.loadFromXML(inputStream);
        }

        URI m3u8Uri = loadUri(properties, "m3u8Uri");
        URI finalM3u8Uri = loadUri(properties, "finalM3u8Uri");
        URI masterM3u8Uri = loadUri(properties, "masterM3u8Uri");

        String finalM3u8Content = properties.getProperty("finalM3u8Content");
        String masterM3u8Content = properties.getProperty("masterM3u8Content");

        return new M3u8Store(m3u8Uri, finalM3u8Uri, masterM3u8Uri, finalM3u8Content, masterM3u8Content);
    }

    public void store(Path m3u8StorePath) throws IOException {
        Preconditions.checkNotNull(m3u8StorePath);
        Preconditions.checkArgument(Files.notExists(m3u8StorePath));

        Properties properties = new Properties();
        {
            setUri(properties, "m3u8Uri", this.m3u8Uri);

            setUri(properties, "masterM3u8Uri", this.masterM3u8Uri);
            setStr(properties, "masterM3u8Content", this.masterM3u8Content);

            setUri(properties, "finalM3u8Uri", this.finalM3u8Uri);
            setStr(properties, "finalM3u8Content", this.finalM3u8Content);
        }

        try(OutputStream outputStream = Files.newOutputStream(m3u8StorePath)) {
            properties.storeToXML(outputStream, "m3u8Store");
        }
    }

    private static URI loadUri(Properties properties, String name) {
        String property = properties.getProperty(name);
        if (StringUtils.isBlank(property)) {
            return null;
        }
        return URI.create(property);
    }

    private void setUri(Properties properties, String name, URI uri) {
        Optional.ofNullable(uri).ifPresent(u -> properties.setProperty(name, u.toString()));
    }

    private void setStr(Properties properties, String name, String prop) {
        Optional.ofNullable(prop).ifPresent(p -> properties.setProperty(name, p));
    }

    public String toPlainString() {
        return "M3u8Store{" +
                "m3u8Uri=" + m3u8Uri +
                ", finalM3u8Uri=" + finalM3u8Uri +
                ", masterM3u8Uri=" + masterM3u8Uri +
                '}';
    }
}
