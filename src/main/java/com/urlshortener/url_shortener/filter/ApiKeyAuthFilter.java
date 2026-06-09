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

public class ApiKeyAuthFilter extends OncePerRequestFilter {
    private static final String API_KEY_HEADER = "X-API-KEY";
    public static final String CURRENT_USER_ATTR = "currentUser";

    private final UserService userService;
    private final HandlerExceptionResolver resolver;

    public ApiKeyAuthFilter(UserService userService, HandlerExceptionResolver resolver) {
        this.userService = userService;
        this.resolver = resolver;
    }

    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String apiKey = request.getHeader(API_KEY_HEADER);

        try {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new HeaderRequiredException(API_KEY_HEADER);
            }
            User user = userService.findByApiKeyWithTier(apiKey);
            request.setAttribute(CURRENT_USER_ATTR, user);

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            resolver.resolveException(request, response, null, e);
            return;
        }

    }

}
