package io.github.kanglong1023.m3u8.http.component;

public class UnexpectedHttpStatusException extends ExplicitlyTerminateIOException {

    public UnexpectedHttpStatusException(String message) {
        super(message);
    }

    public static UnexpectedHttpStatusException throwException(String message) throws ExplicitlyTerminateIOException {
        throw new UnexpectedHttpStatusException(message);
    }

}
