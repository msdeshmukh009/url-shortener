package com.urlshortener.url_shortener.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.urlshortener.url_shortener.filter.ApiKeyAuthFilter;
import com.urlshortener.url_shortener.filter.BlacklistFilter;
import com.urlshortener.url_shortener.filter.TierFilter;
import com.urlshortener.url_shortener.service.BlacklistService;
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
    public FilterRegistrationBean<BlacklistFilter> blacklistFilterRegistration(
            BlacklistService blacklistService,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {

        FilterRegistrationBean<BlacklistFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new BlacklistFilter(blacklistService, resolver));
        reg.addUrlPatterns("/api/*", "/r/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyFilterRegistration() {
        FilterRegistrationBean<ApiKeyAuthFilter> registrationBean = new FilterRegistrationBean<>();

        registrationBean.setFilter(new ApiKeyAuthFilter(userService, resolver));
        registrationBean.addUrlPatterns("/api/shorten", "/api/shorten/*", "/api/urls", "/api/urls/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);

        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<TierFilter> tierFilterRegistration() {
        FilterRegistrationBean<TierFilter> tierRegistrationBean = new FilterRegistrationBean<>();

        tierRegistrationBean.setFilter(new TierFilter(resolver));
        tierRegistrationBean.addUrlPatterns("/api/shorten/bulk");
        tierRegistrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 30);

        return tierRegistrationBean;
    }

}
