package com.urlshortener.url_shortener.filter;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

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

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
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

    /**
     * One limit the request was checked against. `remaining` may be negative
     * when the limit is already exceeded; we clamp before emitting headers.
     */
    private record LimitCheck(String name, long limit, long remaining) {
        boolean exceeded() {
            return remaining < 0;
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            String ip = IpAddressUtil.extractIp(request);
            String apiKey = request.getHeader(API_KEY_HEADER);
            String url = request.getRequestURI();

            boolean isShortening = isPath(url, SHORTEN_ENDPOINT);
            boolean isRedirecting = isPath(url, REDIRECT_ENDPOINT);

            
            long resetEpochSeconds = Instant.now()
                    .truncatedTo(ChronoUnit.MINUTES)
                    .plus(1, ChronoUnit.MINUTES)
                    .getEpochSecond();
            response.setHeader("X-RateLimit-Reset", String.valueOf(resetEpochSeconds));

      
            List<LimitCheck> checks = new ArrayList<>();

            // General IP-based limit (applies to everything).
            checks.add(new LimitCheck(
                    "general",
                    rateLimitProperties.getMaxRequestsPerMin(),
                    rateLimitService.remainingHit(ip, apiKey)));

            // Per-API-key shorten limit (only for /api/shorten with a key).
            if (isShortening && apiKey != null && !apiKey.isBlank()) {
                checks.add(new LimitCheck(
                        "shorten",
                        rateLimitProperties.getMaxShortenRequestsPerMin(),
                        rateLimitService.remainingShortenHit(apiKey)));
            }

            // Per-IP redirect limit (only for /api/redirect).
            if (isRedirecting) {
                checks.add(new LimitCheck(
                        "redirect",
                        rateLimitProperties.getMaxRedirectRequestsPerMin(),
                        rateLimitService.remainingRedirectHit(ip)));
            }

       
            LimitCheck binding = checks.stream()
                    .min((a, b) -> Long.compare(a.remaining(), b.remaining()))
                    .orElseThrow(); // 'general' is always present, so never empty

            response.setHeader("X-RateLimit-Limit", String.valueOf(binding.limit()));
            // never emit a negative remaining
            response.setHeader("X-RateLimit-Remaining",
                    String.valueOf(Math.max(0, binding.remaining())));

            if (binding.exceeded()) {
                log.info("Rate limit exceeded: limit={} ip={} key={}",
                        binding.name(), ip, apiKey);
                throw new RateLimitException();
            }

            filterChain.doFilter(request, response);

        } catch (RateLimitException e) {
            resolver.resolveException(request, response, null, e);
        }
    }

    /**
     * Match the endpoint allowing an optional trailing slash, so /api/shorten
     * and /api/shorten/ are both treated as the shorten endpoint.
     */
    private boolean isPath(String uri, String endpoint) {
        if (uri == null) {
            return false;
        }
        return uri.equalsIgnoreCase(endpoint)
                || uri.equalsIgnoreCase(endpoint + "/");
    }
}