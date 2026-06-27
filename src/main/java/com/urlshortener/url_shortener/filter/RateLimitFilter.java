package com.urlshortener.url_shortener.filter;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

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

    public RateLimitFilter(RateLimitService rateLimitService, HandlerExceptionResolver resolver) {
        this.rateLimitService = rateLimitService;
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

        try {
            //General Ip base rate limiting
            if (rateLimitService.isBlocked(ip, apiKey)) {
                log.info("Rate limit exceeded {}", ip);
                throw new RateLimitException();
            }
            //Api key based rate limiting for /shorten
            if (isShortening && rateLimitService.isShortenBlocked(apiKey)) {
                log.info("Rate limit exceeded for shorten {}", apiKey);
                throw new RateLimitException();
            }
            //Ip based rate limiting for /redirect
            if (isRedirecting && rateLimitService.isRedirectBlocked(ip)) {
                log.info("Rate limit exceeded for redirecting {}", ip);
                throw new RateLimitException();
            }

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            resolver.resolveException(request, response, null, e);
            return;
        }
    }
}