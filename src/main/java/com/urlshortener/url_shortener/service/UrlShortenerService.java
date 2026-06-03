package com.urlshortener.url_shortener.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.urlshortener.url_shortener.entity.UrlShortener;
import com.urlshortener.url_shortener.entity.User;
import com.urlshortener.url_shortener.exception.ForbiddenException;
import com.urlshortener.url_shortener.exception.ShortCodeNotFoundException;
import com.urlshortener.url_shortener.exception.ShortCodeTakenException;
import com.urlshortener.url_shortener.exception.UrlExpiredException;
import com.urlshortener.url_shortener.repository.UrlShortenerRepository;

import jakarta.transaction.Transactional;

@Service
public class UrlShortenerService {
    private final UrlShortenerRepository repository;

    public UrlShortenerService(UrlShortenerRepository repository) {
        this.repository = repository;
    }

    public record ShortenResult(UrlShortener mapping) {
    }

    private String resolveShortCode(String providedShortCode) {
        if (providedShortCode != null) {
            if (repository.findByShortCode(providedShortCode).isPresent()) {
                throw new ShortCodeTakenException(providedShortCode);
            }
            return providedShortCode;
        }

        return generateUniqueCode();
    }

    public ShortenResult shorten(String originalUrl, User user, Instant expiresAt, String providedShortCode) {
        String shortCode = resolveShortCode(providedShortCode);
        String normalized = normalizeUrl(originalUrl);
        UrlShortener mapping = UrlShortener.builder()
                .originalUrl(normalized)
                .shortCode(shortCode)
                .user(user)
                .expiresAt(expiresAt)
                .build();
        try {
            return new ShortenResult(repository.save(mapping));
        } catch (DataIntegrityViolationException e) {
            throw new ShortCodeTakenException(shortCode);
        }
    }

    private String normalizeUrl(String url) {
        return url.trim().toLowerCase();
    }

    @Transactional
    public String resolve(String shortCode) {
        UrlShortener mapping = repository.findByShortCodeAndIsDeletedFalse(shortCode)
                .orElseThrow(() -> new ShortCodeNotFoundException(shortCode));

        if (mapping.getExpiresAt() != null && mapping.getExpiresAt().isBefore(Instant.now())) {
            throw new UrlExpiredException(shortCode);
        }

        mapping.setVisitCount(mapping.getVisitCount() + 1);
        mapping.setLastAccessedAt(LocalDateTime.now());
        repository.save(mapping);
        return mapping.getOriginalUrl();
    }

    public UrlShortener findByShortCode(String shortCode) {
        return repository.findByShortCode(shortCode).orElse(null);
    }

    @Transactional
    public void deleteShortCode(String shortCode, Integer userId) {
        UrlShortener mapping = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortCodeNotFoundException(shortCode));

        if (!userId.equals(mapping.getUser().getId())) {
            throw new ForbiddenException("You don't own this short code");
        }

        if (Boolean.TRUE.equals(mapping.getIsDeleted())) {
            throw new ShortCodeNotFoundException(shortCode);
        }

        mapping.setIsDeleted(true);
        mapping.setDeletedAt(LocalDateTime.now());
        repository.save(mapping);
    }

    private String generateUniqueCode() {
        UUID uuid = UUID.randomUUID();
        String uuidString = uuid.toString().replace("-", "").substring(0, 10);
        return uuidString;
    }
}
