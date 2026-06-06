package com.urlshortener.url_shortener.exception;

public class InvalidApiKeyException extends RuntimeException {
        public InvalidApiKeyException() {
        super("Invalid api key");
    }
}
