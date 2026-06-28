package com.urlshortener.url_shortener.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Component
@ConfigurationProperties(prefix = "ratelimit")
@Data
public class RateLimitProperties {
    private int maxRequestsPerMin = 100;

    private int maxShortenRequestsPerMin = 10;
    
    private int maxRedirectRequestsPerMin = 50;
}