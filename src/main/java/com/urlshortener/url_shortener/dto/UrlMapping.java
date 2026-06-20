package com.urlshortener.url_shortener.dto;

import java.time.Instant;

import com.urlshortener.url_shortener.entity.UrlShortener;

public record UrlMapping(String originalUrl, Instant expiresAt, String passwordHash) {
    public static UrlMapping from(UrlShortener mapping) {
        return new UrlMapping(mapping.getOriginalUrl(), mapping.getExpiresAt(), mapping.getPasswordHash());
    }
}
