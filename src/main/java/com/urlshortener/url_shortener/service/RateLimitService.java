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
    private static final String USER_API_KEY_PREFIX = "ratelimit:apiKey:";
    private static final String API_KEY_SHORTEN_PREFIX = "ratelimit:apiKey:shorten";
    private static final String API_KEY_REDIRECT_PREFIX = "ratelimit:ip:redirect";

    @Value("${ratelimit.max-requests-per-min:100}")
    private int ALLOWED_REQUEST_PER_MIN;

    @Value("${ratelimit.max-shorten-requests-per-min:10}")
    private int ALLOWED_SHORTEN_REQUEST_PER_MIN;

    @Value("${ratelimit.max-redirect-requests-per-min:50}")
    private int ALLOWED_REDIRECT_REQUEST_PER_MIN;

    private final RedisTemplate<String, Long> userRateLimitRedisTemplate;

    public RateLimitService(RedisTemplate<String, Long> userRateLimitRedisTemplate) {
        this.userRateLimitRedisTemplate = userRateLimitRedisTemplate;
    }

    private String userIpCacheKey(String ip) {
        return USER_IP_KEY_PREFIX + ip;
    }

    private String userApiCacheKey(String ip) {
        return USER_API_KEY_PREFIX + ip;
    }

    private String ApiKeyShortenCacheKey(String apiKey) {
        return API_KEY_SHORTEN_PREFIX + apiKey;
    }

    private String IpRedirectCacheKey(String ip) {
        return API_KEY_REDIRECT_PREFIX + ip;
    }

    private long loadHitCountByUser(String ip) {
        String key = userIpCacheKey(ip);
        try {
            Long count = userRateLimitRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                userRateLimitRedisTemplate.expire(key, USER_IP_CACHE_TTL);
            }

            log.info("ip={} hitCount={}", ip, count);
            return count == null ? 0 : count;
        } catch (Exception e) {
            log.warn("Error loading hit count for ip {}", ip, e);
            return 0;
        }
    }

    private long incrementHitCountByApiKeyUser(String apiKey) {
        String apiCacheKey = userApiCacheKey(apiKey);
        try {
            Long apiKeyHitCount = userRateLimitRedisTemplate.opsForValue().increment(apiCacheKey);

            if (apiKeyHitCount != null && apiKeyHitCount == 1L) {
                userRateLimitRedisTemplate.expire(apiCacheKey, USER_IP_CACHE_TTL);
            }
            log.info("incrementHitCountByApiKeyUser/apiKey={} hitCount={}", apiKey, apiKeyHitCount);
            return apiKeyHitCount == null ? 0 : apiKeyHitCount;
        } catch (Exception e) {
            log.warn("Error loading hit count for ip {}", apiKey, e);
            return 0;
        }
    }

    public long getHitCountByApiKeyUser(String apiKey) {
        String apiCacheKey = userApiCacheKey(apiKey);
        try {
            Long apiKeyHitCount = userRateLimitRedisTemplate.opsForValue().get(apiCacheKey);

            log.info("apiKey={} hitCount={}", apiKey, apiKeyHitCount);
            return apiKeyHitCount == null ? 0 : apiKeyHitCount;
        } catch (Exception e) {
            log.warn("Error loading hit count for ip {}", apiKey, e);
            return 0;
        }
    }

    private long loadHitCountForShorten(String apiKey) {
        String key = ApiKeyShortenCacheKey(apiKey);
        try {
            Long count = userRateLimitRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                userRateLimitRedisTemplate.expire(key, USER_IP_CACHE_TTL);
            }
            log.info("apiKey={} hitCount={}", apiKey, count);
            return count == null ? 0 : count;
        } catch (Exception e) {
            log.warn("Error loading hit count for apiKey {}", apiKey, e);
            return 0;
        }
    }

    private long loadHitCountForRedirect(String ip) {
        String key = IpRedirectCacheKey(ip);
        try {
            Long count = userRateLimitRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                userRateLimitRedisTemplate.expire(key, USER_IP_CACHE_TTL);
            }
            log.info("ip={} hitCount={}", ip, count);
            return count == null ? 0 : count;
        } catch (Exception e) {
            log.warn("Error loading hit count for ip {}", ip, e);
            return 0;
        }
    }

    public boolean isBlocked(String ip, String apiKey) {
        if (apiKey != null) {
            incrementHitCountByApiKeyUser(apiKey);
        }

        if (ip != null && !ip.isBlank() && loadHitCountByUser(ip) > ALLOWED_REQUEST_PER_MIN) {
            return true;
        }
        return false;
    }

    public boolean isShortenBlocked(String apiKey) {
        long hitCount = loadHitCountForShorten(apiKey);
        log.info("RateLimitService/Shorten Hit count {}", hitCount);
        if (apiKey != null && !apiKey.isBlank() && hitCount > ALLOWED_SHORTEN_REQUEST_PER_MIN) {
            return true;
        }
        return false;
    }

    public boolean isRedirectBlocked(String ip) {
        long hitCount = loadHitCountForRedirect(ip);
        log.info("RateLimitService/Redirect Hit count {}", hitCount);
        if (ip != null && !ip.isBlank() && hitCount > ALLOWED_REDIRECT_REQUEST_PER_MIN) {
            return true;
        }
        return false;
    }
}