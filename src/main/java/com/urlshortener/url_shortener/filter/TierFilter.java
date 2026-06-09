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

public class TierFilter extends OncePerRequestFilter {
    private static final String CURRENT_USER_ATTR = "currentUser";

    private final HandlerExceptionResolver resolver;

    public TierFilter(HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            User user = (User) request.getAttribute(CURRENT_USER_ATTR);
            if (user != null) {
                if (!user.getTier().isCanUseBulkCreation()) {
                    throw new TierRestrictedException();
                }
            }
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            resolver.resolveException(request, response, null, e);
        }
    }
}
