package io.shty.shortener.exception;

public class DuplicateLongUrlException extends RuntimeException{
    public final String longUrl;

    public DuplicateLongUrlException(String longUrl) {
        this.longUrl = longUrl;
    }
}
