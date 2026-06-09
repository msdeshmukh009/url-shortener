package com.urlshortener.url_shortener.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.urlshortener.url_shortener.filter.ApiKeyAuthFilter;
import com.urlshortener.url_shortener.service.UserService;

@Configuration
public class FilterConfig {
    private final UserService userService;
    private final HandlerExceptionResolver resolver;

    public FilterConfig(UserService userService,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.userService = userService;
        this.resolver = resolver;
    }

    @Bean
    public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyFilterRegistration() {
        FilterRegistrationBean<ApiKeyAuthFilter> registrationBean = new FilterRegistrationBean<>();

        registrationBean.setFilter(new ApiKeyAuthFilter(userService, resolver));
        registrationBean.addUrlPatterns("/api/shorten", "/api/shorten/*", "/api/urls", "/api/urls/*");
        registrationBean.setOrder(1);

        return registrationBean;
    }
}
