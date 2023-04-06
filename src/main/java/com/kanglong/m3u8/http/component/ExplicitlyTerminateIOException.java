package com.kanglong.m3u8.http.component;

import java.io.IOException;

public class ExplicitlyTerminateIOException extends IOException {

    public ExplicitlyTerminateIOException() {
    }

    public ExplicitlyTerminateIOException(String message) {
        super(message);
    }

    public ExplicitlyTerminateIOException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExplicitlyTerminateIOException(Throwable cause) {
        super(cause);
    }
}
