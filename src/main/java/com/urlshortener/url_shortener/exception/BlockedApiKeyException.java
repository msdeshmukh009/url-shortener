package com.urlshortener.url_shortener.exception;

public class BlockedApiKeyException extends RuntimeException {
    public BlockedApiKeyException() {
        super("Your API access has been suspended. Contact support.");
    }
}