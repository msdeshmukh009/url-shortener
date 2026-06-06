package com.urlshortener.url_shortener.dto;

import java.time.Instant;
import java.time.LocalDateTime;

import com.urlshortener.url_shortener.entity.UrlShortener;

public record ShortenResponse(
        Integer id,
        String originalUrl,
        String shortCode,
        LocalDateTime createdAt,
        Integer visitCount,
        LocalDateTime lastAccessedAt,
        Instant expiresAt,
        Integer userId,
        Boolean isPasswordProtected
) {
    public static ShortenResponse from(UrlShortener entity) {
        return new ShortenResponse(
                entity.getId(),
                entity.getOriginalUrl(),
                entity.getShortCode(),
                entity.getCreatedAt(),
                entity.getVisitCount(),
                entity.getLastAccessedAt(),
                entity.getExpiresAt(),
                entity.getUser() != null ? entity.getUser().getId() : null,
                entity.getPasswordHash() != null
        );
    }
}
