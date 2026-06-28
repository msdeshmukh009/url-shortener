package com.urlshortener.url_shortener.filter;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.urlshortener.url_shortener.config.RateLimitProperties;
import com.urlshortener.url_shortener.exception.RateLimitException;
import com.urlshortener.url_shortener.service.RateLimitService;
import com.urlshortener.url_shortener.utils.IpAddressUtil;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final String SHORTEN_ENDPOINT = "/api/shorten";
    private static final String REDIRECT_ENDPOINT = "/api/redirect";
    private final RateLimitService rateLimitService;
    private final HandlerExceptionResolver resolver;
    private final RateLimitProperties rateLimitProperties;

    public RateLimitFilter(RateLimitService rateLimitService, HandlerExceptionResolver resolver,
            RateLimitProperties rateLimitProperties) {
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String ip = IpAddressUtil.extractIp(request);
        String apiKey = request.getHeader(API_KEY_HEADER);
        String url = request.getRequestURI();
        Boolean isShortening = SHORTEN_ENDPOINT.equalsIgnoreCase(url);
        Boolean isRedirecting = REDIRECT_ENDPOINT.equalsIgnoreCase(url);
        Instant nextWindowStart = Instant.now()
                .truncatedTo(ChronoUnit.MINUTES)
                .plus(1, ChronoUnit.MINUTES);

        long resetEpochSeconds = nextWindowStart.getEpochSecond();

        response.setHeader("X-RateLimit-Reset", String.valueOf(resetEpochSeconds));

        try {
            // General Ip base rate limiting
            Long remainingHits = rateLimitService.remainingHit(ip, apiKey);
            response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimitProperties.getMaxRequestsPerMin()));
            if (remainingHits < 0) {
                log.info("Rate limit exceeded {}", ip);
                throw new RateLimitException();
            }else {
                response.setHeader("X-RateLimit-Remaining", remainingHits.toString());
            }
            
            // Api key based rate limiting for /shorten
            if (isShortening && apiKey != null &&!apiKey.isBlank()) {
                Long remainingShortenHit = rateLimitService.remainingShortenHit(apiKey);
                response.setHeader("X-RateLimit-Limit",String.valueOf(rateLimitProperties.getMaxShortenRequestsPerMin()));
                if (remainingShortenHit < 0) {
                    throw new RateLimitException();
                } else {
                    response.setHeader("X-RateLimit-Remaining", remainingShortenHit.toString());
                }
            }
            // Ip based rate limiting for /redirect
            if (isRedirecting) {
                response.setHeader("X-RateLimit-Limit",String.valueOf(rateLimitProperties.getMaxRedirectRequestsPerMin()));
                Long remainingRedirectHits = rateLimitService.remainingRedirectHit(ip);
                if (remainingRedirectHits < 0) {
                    throw new RateLimitException();
                } else {
                    response.setHeader("X-RateLimit-Remaining", remainingRedirectHits.toString());
                }
            }

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            resolver.resolveException(request, response, null, e);
            return;
        }
    }
}