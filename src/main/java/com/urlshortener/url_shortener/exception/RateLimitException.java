package com.urlshortener.url_shortener.exception;

public class RateLimitException extends RuntimeException {
    public RateLimitException(){
        super("Too many requests");
    }   
}
