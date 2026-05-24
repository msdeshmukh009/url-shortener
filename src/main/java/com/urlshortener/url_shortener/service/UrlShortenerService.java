package com.urlshortener.url_shortener.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.urlshortener.url_shortener.entity.UrlShortener;
import com.urlshortener.url_shortener.repository.UrlShortenerRepository;

import jakarta.transaction.Transactional;

@Service
public class UrlShortenerService {
    private final UrlShortenerRepository repository;

    public UrlShortenerService(UrlShortenerRepository repository) {
        this.repository = repository;
    }

    public UrlShortener shorten(String originalUrl) {
        String shortCode = generateUniqueCode();
        UrlShortener mapping = UrlShortener.builder()
                .originalUrl(originalUrl)
                .shortCode(shortCode).build();
        return repository.save(mapping);
    }
    
    @Transactional
    public String resolve(String shortCode) {
        UrlShortener mapping = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new RuntimeException("Short code not found" + shortCode));
        System.out.println("visit count: " + mapping.getVisitCount());
        mapping.setVisitCount(mapping.getVisitCount() + 1);
        repository.save(mapping);
        return mapping.getOriginalUrl();
    }

    private String generateUniqueCode() {
        UUID uuid = UUID.randomUUID();
        String uuidString = uuid.toString().replace("-", "").substring(0, 10);
        return uuidString;
    }
}
