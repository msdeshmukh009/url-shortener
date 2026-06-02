package com.urlshortener.url_shortener.dto;

import java.time.LocalDateTime;

import com.urlshortener.url_shortener.entity.UrlShortener;

public record ShortenResponse(
        Integer id,
        String originalUrl,
        String shortCode,
        LocalDateTime createdAt,
        Integer visitCount,
        LocalDateTime lastAccessedAt,
        Integer userId
) {
    public static ShortenResponse from(UrlShortener entity) {
        return new ShortenResponse(
                entity.getId(),
                entity.getOriginalUrl(),
                entity.getShortCode(),
                entity.getCreatedAt(),
                entity.getVisitCount(),
                entity.getLastAccessedAt(),
                entity.getUser() != null ? entity.getUser().getId() : null
        );
    }
}
