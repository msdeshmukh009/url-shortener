package com.urlshortener.url_shortener.service;

import org.springframework.stereotype.Service;

import com.urlshortener.url_shortener.entity.User;
import com.urlshortener.url_shortener.exception.InvalidApiKeyException;
import com.urlshortener.url_shortener.repository.UserRepository;

@Service
public class UserService {
    private final UserRepository repository;

    UserService(UserRepository repository) {
        this.repository = repository;
    }

    public User findByApiKey(String apiKey) {
        User user = repository.findByApiKey(apiKey).orElseThrow(() -> new InvalidApiKeyException());
        return user;
    }

    public User findByApiKeyWithTier(String apiKey) {
        User user = repository.findByApiKeyWithTier(apiKey).orElseThrow(() -> new InvalidApiKeyException());
        return user;
    }
}
