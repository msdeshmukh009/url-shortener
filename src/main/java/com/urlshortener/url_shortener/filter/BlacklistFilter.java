package com.urlshortener.url_shortener.filter;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.urlshortener.url_shortener.exception.BlockedApiKeyException;
import com.urlshortener.url_shortener.service.BlacklistService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlacklistFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private final BlacklistService blacklistService;
    private final HandlerExceptionResolver resolver;

    public BlacklistFilter(BlacklistService blacklistService, HandlerExceptionResolver resolver) {
        this.blacklistService = blacklistService;
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long filterStart = System.nanoTime();
        long preWorkNs = 0;
        String apiKey = request.getHeader(API_KEY_HEADER);

        try {
            if (apiKey != null && blacklistService.isBlocked(apiKey)) {
                throw new BlockedApiKeyException();
            }
            preWorkNs = System.nanoTime() - filterStart;
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            if (preWorkNs == 0) {
                preWorkNs = System.nanoTime() - filterStart;
            }
            resolver.resolveException(request, response, null, e);
            return;
        } finally {
            long ownTimeMs = preWorkNs / 1_000_000;
            log.debug("Filter Logging {}: own_time={}ms", getClass().getSimpleName(), ownTimeMs);

        }
    }
}