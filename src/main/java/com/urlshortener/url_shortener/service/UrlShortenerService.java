package com.urlshortener.url_shortener.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
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

    public record ShortenResult(UrlShortener mapping, boolean created) {}

    public ShortenResult shorten(String originalUrl) {
        String normalized = normalizeUrl(originalUrl);
        try {
            Optional<UrlShortener> preExistingMapping = repository.findByOriginalUrl(normalized);
            if (preExistingMapping.isPresent()) {
                return new ShortenResult(preExistingMapping.get(), false);
            }

            String shortCode = generateUniqueCode();
            UrlShortener mapping = UrlShortener.builder()
                    .originalUrl(normalized)
                    .shortCode(shortCode).build();
            return new ShortenResult(repository.save(mapping), true);
        } catch (DataIntegrityViolationException e) {
            UrlShortener  original = repository.findByOriginalUrl(normalized)
                    .orElseThrow(() -> new RuntimeException("Failed to shorten the url"));
            return new ShortenResult(original, false);
        }
    }

    private String normalizeUrl(String url) {
        return url.trim().toLowerCase();
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
