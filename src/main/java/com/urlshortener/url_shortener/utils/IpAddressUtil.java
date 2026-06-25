package com.urlshortener.url_shortener.utils;

import jakarta.servlet.http.HttpServletRequest;

public class IpAddressUtil {
    public static String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim(); // first IP is the original client
        }
        return request.getRemoteAddr();
    }
}
