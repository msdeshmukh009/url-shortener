package com.urlshortener.url_shortener.filter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jboss.logging.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private static final List<String> SKIP_PREFIX_PATHS = List.of(
            "/actuator/",
            "/static/",
            "/css/",
            "/js/",
            "/images/");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Instant startTime = Instant.now();

        try {
            filterChain.doFilter(request, response);
            
        } finally {
            logRequest(request, response, startTime);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        if (path.startsWith("/api/")) {
            return false;
        }

        if (path.startsWith("/actuator/")) {
            return true;
        }

        return SKIP_PREFIX_PATHS.stream().anyMatch(path::startsWith);
    }

    private void logRequest(HttpServletRequest request, HttpServletResponse response, Instant startTime) {
        long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();

        String method = request.getMethod();
        String url = request.getRequestURI();
        String query = request.getQueryString();
        String userAgent = request.getHeader("User-Agent");
        String ip = extractClientIp(request);
        int status = response.getStatus();
        String requestId = UUID.randomUUID().toString();

        MDC.put("method", method);
        MDC.put("url", url);
        MDC.put("query", query);
        MDC.put("ua", userAgent);
        MDC.put("status", String.valueOf(status));
        MDC.put("durationMs", String.valueOf(durationMs));
        MDC.put("clientIp", ip);
        MDC.put("requestId", requestId);

        response.setHeader("X-Request-Id", requestId);

        try {
            log.info("Request processed");
        } finally {
            MDC.clear();
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim(); // first IP is the original client
        }
        return request.getRemoteAddr();
    }
}