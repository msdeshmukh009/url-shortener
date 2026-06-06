package com.urlshortener.url_shortener.exception;

public class PasswordRequiredException extends RuntimeException {

    public PasswordRequiredException(String shortCode) {
        super("This URL is password-protected. Visit https://url-shortener-s9py.onrender.com/r/"+ shortCode + " to enter the password.");
    }
}
