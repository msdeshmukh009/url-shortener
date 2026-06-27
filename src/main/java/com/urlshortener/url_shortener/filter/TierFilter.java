package com.urlshortener.url_shortener.filter;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.urlshortener.url_shortener.entity.User;
import com.urlshortener.url_shortener.exception.RateLimitException;
import com.urlshortener.url_shortener.exception.TierRestrictedException;
import com.urlshortener.url_shortener.service.RateLimitService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TierFilter extends OncePerRequestFilter {
    private static final String CURRENT_USER_ATTR = "currentUser";
    private static final String BULK_SHORTEN_ENDPOINT = "/api/shorten/bulk";
    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final RateLimitService rateLimitService;

    private final HandlerExceptionResolver resolver;

    public TierFilter(HandlerExceptionResolver resolver, RateLimitService rateLimitService) {
        this.resolver = resolver;
        this.rateLimitService = rateLimitService;
    }

    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long filterStart = System.nanoTime();
        long preWorkNs = 0;
        try {
            User user = (User) request.getAttribute(CURRENT_USER_ATTR);
            Integer rateLimitPerMin = user.getTier().getRateLimitPerMin();
            String apiKey = request.getHeader(API_KEY_HEADER);
            String url = request.getRequestURI();
            Boolean isBulkShortening = BULK_SHORTEN_ENDPOINT.equals(url);
            if (user != null) {
                if (isBulkShortening && !user.getTier().isCanUseBulkCreation()) {
                    throw new TierRestrictedException();
                }

                if (rateLimitPerMin != null && rateLimitService.getHitCountByApiKeyUser(apiKey) > rateLimitPerMin) {
                    throw new RateLimitException();
                }
            }
            preWorkNs = System.nanoTime() - filterStart;
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            if (preWorkNs == 0) {
                preWorkNs = System.nanoTime() - filterStart;
            }
            log.warn("Error/TierFilter {}", e.toString());
            resolver.resolveException(request, response, null, e);
        } finally {
            long ownTimeMs = preWorkNs / 1_000_000;
            log.debug("Filter Logging {}: own_time={}ms", getClass().getSimpleName(), ownTimeMs);

        }
    }
}
