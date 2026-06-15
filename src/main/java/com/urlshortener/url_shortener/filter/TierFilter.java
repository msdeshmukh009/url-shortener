package com.urlshortener.url_shortener.filter;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.urlshortener.url_shortener.entity.User;
import com.urlshortener.url_shortener.exception.TierRestrictedException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TierFilter extends OncePerRequestFilter {
    private static final String CURRENT_USER_ATTR = "currentUser";
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private final HandlerExceptionResolver resolver;

    public TierFilter(HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long filterStart = System.nanoTime();
        long preWorkNs = 0;
        try {
            User user = (User) request.getAttribute(CURRENT_USER_ATTR);
            if (user != null) {
                if (!user.getTier().isCanUseBulkCreation()) {
                    throw new TierRestrictedException();
                }
            }
            preWorkNs = System.nanoTime() - filterStart;
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            if (preWorkNs == 0) {
                preWorkNs = System.nanoTime() - filterStart;
            }
            resolver.resolveException(request, response, null, e);
        } finally {
            long ownTimeMs = preWorkNs / 1_000_000;
            log.debug("Filter Logging {}: own_time={}ms", getClass().getSimpleName(), ownTimeMs);

        }
    }
}
