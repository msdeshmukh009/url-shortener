package com.urlshortener.url_shortener.exception;

public class InvalidPasswordException  extends RuntimeException {
       public InvalidPasswordException(String shortCode) {
        super("Invalid Password for: " + shortCode);
    }
}
