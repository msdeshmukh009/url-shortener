package com.urlshortener.url_shortener.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimitService {
    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private static final Duration USER_IP_CACHE_TTL = Duration.ofSeconds(60);
    private static final String USER_IP_KEY_PREFIX = "ratelimit:ip:";

    @Value("${ratelimit.max-requests-per-min:100}")
    private int ALLOWED_REQUEST_PER_MIN;

    private final RedisTemplate<String, Long> userIpRedisTemplate;

    public RateLimitService(RedisTemplate<String, Long> userIpRedisTemplate) {
        this.userIpRedisTemplate = userIpRedisTemplate;
    }

    private String userIpCacheKey(String ip) {
        return USER_IP_KEY_PREFIX + ip;
    }

    private long loadHitCountByUser(String ip) {
        String key = userIpCacheKey(ip);
        try {
            Long count = userIpRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                userIpRedisTemplate.expire(key, USER_IP_CACHE_TTL);
            }
            log.info("ip={} hitCount={}", ip, count);
            return count == null ? 0 : count;
        } catch (Exception e) {
            log.warn("Error loading hit count for ip {}", ip, e);
            return 0;
        }
    }

    public boolean isBlocked(String ip) {
        System.out.println("BlacklistService/isBlocked: ip" + ip);
        if (ip != null && !ip.isBlank() && loadHitCountByUser(ip) > ALLOWED_REQUEST_PER_MIN) {
            return true;
        }
        return false;
    }
}