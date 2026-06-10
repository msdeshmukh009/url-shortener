package com.urlshortener.url_shortener.filter;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.urlshortener.url_shortener.entity.User;
import com.urlshortener.url_shortener.exception.HeaderRequiredException;
import com.urlshortener.url_shortener.service.UserService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApiKeyAuthFilter extends OncePerRequestFilter {
    private static final String API_KEY_HEADER = "X-API-KEY";
    public static final String CURRENT_USER_ATTR = "currentUser";

    private final UserService userService;
    private final HandlerExceptionResolver resolver;

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    public ApiKeyAuthFilter(UserService userService, HandlerExceptionResolver resolver) {
        this.userService = userService;
        this.resolver = resolver;
    }

    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long filterStart = System.nanoTime();
        long preWorkNs = 0;
        String apiKey = request.getHeader(API_KEY_HEADER);

        try {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new HeaderRequiredException(API_KEY_HEADER);
            }
            User user = userService.findByApiKeyWithTier(apiKey);
            request.setAttribute(CURRENT_USER_ATTR, user);
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
