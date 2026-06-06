package com.urlshortener.url_shortener.dto;

import com.urlshortener.url_shortener.enums.OutcomeType;

public record ResolveOutcome(
        OutcomeType type,
        String originalUrl) {
}
