package com.urlshortener.url_shortener.exception;

public class UrlExpiredException extends RuntimeException {
    public UrlExpiredException(String shortCode) {
        super("Short code has expired: " + shortCode);
    }
}
