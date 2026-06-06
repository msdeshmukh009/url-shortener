package com.urlshortener.url_shortener.exception;

public class ShortCodeTakenException extends RuntimeException {
    public ShortCodeTakenException(String shortCode){
        super("Short code '" + shortCode + "' is already taken. Please try a different one.");
    }
}
