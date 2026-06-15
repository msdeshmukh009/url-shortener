package com.urlshortener.url_shortener.exception;

public class HeaderRequiredException extends RuntimeException {
    public HeaderRequiredException(String headerName) {
        super("Required header '" + headerName + "' is not present.");
    }
}
