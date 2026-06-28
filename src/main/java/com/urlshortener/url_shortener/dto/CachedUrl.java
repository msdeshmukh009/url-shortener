package com.urlshortener.url_shortener.dto;

import java.time.Instant;

import com.urlshortener.url_shortener.entity.UrlShortener;

public record CachedUrl(
        Integer id,
        String shortCode,
        String originalUrl,
        Instant expiresAt,
        boolean hasPassword
) {
    public static CachedUrl from(UrlShortener entity) {
        return new CachedUrl(
                entity.getId(),
                entity.getShortCode(),
                entity.getOriginalUrl(),
                entity.getExpiresAt(),
                entity.getPasswordHash() != null
        );
    }
}