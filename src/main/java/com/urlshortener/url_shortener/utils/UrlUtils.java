package com.urlshortener.url_shortener.utils;

import java.net.URI;
import java.util.UUID;

public final class UrlUtils {
    private UrlUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String generateRandomUrl() {
        String randomPrefix = UUID.randomUUID().toString().replace("-", "").substring(0, 9);
        return URI.create("https://" + randomPrefix + ".com").toString();
    }
}
