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
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.urlshortener.url_shortener.entity.User;

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
        long filterStart = System.nanoTime();
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        Instant start = Instant.now();
        String requestId = UUID.randomUUID().toString();
        wrappedResponse.setHeader("X-Request-Id", requestId);
        long beforeChain = System.nanoTime();
        long preWork = beforeChain - filterStart;

        try {
            filterChain.doFilter(request, wrappedResponse);
        } finally {
            long afterChain = System.nanoTime();
            try {
                long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();
                wrappedResponse.setHeader("X-Duration-Ms", String.valueOf(durationMs));
                logRequest(request, wrappedResponse, start, requestId);
            } catch (Exception e) {
                log.error("Failed to log request", e);
            } finally {
                wrappedResponse.copyBodyToResponse();
            }
            long filterEnd = System.nanoTime();
            long postWork = filterEnd - afterChain;

            long ownTime = preWork + postWork;
            long ownTimeMs = ownTime / 1_000_000;
            log.debug("Filter Logging {}: own_time={}ms", getClass().getSimpleName(), ownTimeMs);
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

    private void logRequest(HttpServletRequest request, HttpServletResponse response, Instant startTime,
            String requestId) {
        long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();

        String method = request.getMethod();
        String url = request.getRequestURI();
        String query = request.getQueryString();
        String userAgent = request.getHeader("User-Agent");
        String ip = extractClientIp(request);
        int status = response.getStatus();

        MDC.put("method", method);
        MDC.put("url", url);
        MDC.put("query", query);
        MDC.put("ua", userAgent);
        MDC.put("status", String.valueOf(status));
        MDC.put("durationMs", String.valueOf(durationMs));
        MDC.put("clientIp", ip);
        MDC.put("requestId", requestId);

        User user = (User) request.getAttribute(ApiKeyAuthFilter.CURRENT_USER_ATTR);
        if (user != null) {
            MDC.put("userId", String.valueOf(user.getId()));
            if (user.getTier() != null) {
                MDC.put("userTier", user.getTier().getName());
            }
        }

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