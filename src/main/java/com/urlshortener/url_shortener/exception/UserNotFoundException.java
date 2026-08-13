package com.urlshortener.url_shortener.exception;

public class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(Integer userId) {
        super("User not found: " + userId.toString());
    }
}
