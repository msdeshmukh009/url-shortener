package com.urlshortener.url_shortener.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.urlshortener.url_shortener.controller.UrlShortenerController.ShortenRequest;
import com.urlshortener.url_shortener.dto.BulkShortenResponse;
import com.urlshortener.url_shortener.dto.UnitShortenResponse;
import com.urlshortener.url_shortener.entity.UrlShortener;
import com.urlshortener.url_shortener.entity.User;
import com.urlshortener.url_shortener.exception.ForbiddenException;
import com.urlshortener.url_shortener.exception.ShortCodeNotFoundException;
import com.urlshortener.url_shortener.exception.ShortCodeTakenException;
import com.urlshortener.url_shortener.exception.TierRestrictedException;
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

    @Transactional
    public ShortenResult edit(User user, Instant expiresAt, String shortCode) {
        UrlShortener mapping = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortCodeNotFoundException(shortCode));
        if (!mapping.getUser().equals(user)) {
            throw new ForbiddenException("You don't own this short code");
        }

        if (expiresAt != null) {
            mapping.setExpiresAt(expiresAt);
        }

        return new ShortenResult(repository.save(mapping));

    }

    public BulkShortenResponse bulkShorten(User user, List<ShortenRequest> urls) {
        List<UnitShortenResponse> unitResponses = new ArrayList<>();

        if (!user.getTier().isCanUseBulkCreation()) {
            throw new TierRestrictedException();
        }

        for (int i = 0; i < urls.size(); i++) {
            ShortenRequest shortenRequest = urls.get(i);
            try {
                ShortenResult result = shorten(shortenRequest.originalUrl(), user, shortenRequest.expiresAt(),
                        shortenRequest.shortCode());

                unitResponses.add(UnitShortenResponse.success(i, result.mapping));
            } catch (ShortCodeTakenException e) {
                unitResponses.add(UnitShortenResponse.failure(i,
                        shortenRequest.originalUrl(), "Short code already taken"));
            } catch (Exception e) {
                unitResponses.add(UnitShortenResponse.failure(i,
                        shortenRequest.originalUrl(), "Internal error processing this URL"));
            }
        }

        return BulkShortenResponse.from(unitResponses);
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
