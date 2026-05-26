package com.urlshortener.url_shortener.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.urlshortener.url_shortener.entity.UrlShortener;
import com.urlshortener.url_shortener.exception.ShortCodeNotFoundException;
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
                .orElseThrow(() -> new ShortCodeNotFoundException(shortCode));

        mapping.setVisitCount(mapping.getVisitCount() + 1);
        repository.save(mapping);
        return mapping.getOriginalUrl();
    }

    @Transactional
    public void deleteShortCode(String shortCode) {
        long deletedRecord = repository.deleteByShortCode(shortCode);
        if(deletedRecord == 0){
            throw new ShortCodeNotFoundException(shortCode);
        }
    }

    private String generateUniqueCode() {
        UUID uuid = UUID.randomUUID();
        String uuidString = uuid.toString().replace("-", "").substring(0, 10);
        return uuidString;
    }
}
