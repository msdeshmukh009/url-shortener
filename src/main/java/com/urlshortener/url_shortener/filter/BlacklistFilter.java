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

public class BlacklistFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-KEY";

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

        String apiKey = request.getHeader(API_KEY_HEADER);

        try {
            if (apiKey != null && blacklistService.isBlocked(apiKey)) {
                throw new BlockedApiKeyException();
            }
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            resolver.resolveException(request, response, null, e);
            return;
        }
    }
}