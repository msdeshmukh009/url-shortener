package com.urlshortener.url_shortener.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.TransientDataAccessException;

import com.urlshortener.url_shortener.controller.UrlShortenerController.ShortenRequest;
import com.urlshortener.url_shortener.dto.BulkShortenResponse;
import com.urlshortener.url_shortener.dto.CachedUrl;
import com.urlshortener.url_shortener.dto.ResolveOutcome;
import com.urlshortener.url_shortener.dto.ShortenResponse;
import com.urlshortener.url_shortener.dto.UnitShortenResponse;
import com.urlshortener.url_shortener.entity.UrlShortener;
import com.urlshortener.url_shortener.entity.User;
import com.urlshortener.url_shortener.enums.OutcomeType;
import com.urlshortener.url_shortener.exception.ForbiddenException;
import com.urlshortener.url_shortener.exception.InvalidPasswordException;
import com.urlshortener.url_shortener.exception.PasswordRequiredException;
import com.urlshortener.url_shortener.exception.ServiceUnavailableException;
import com.urlshortener.url_shortener.exception.ShortCodeNotFoundException;
import com.urlshortener.url_shortener.exception.ShortCodeTakenException;
import com.urlshortener.url_shortener.exception.UrlExpiredException;
import com.urlshortener.url_shortener.queue.VisitCountBatcher;
import com.urlshortener.url_shortener.repository.UrlShortenerRepository;
import com.urlshortener.url_shortener.resilience.SimpleCircuitBreaker;

import jakarta.transaction.Transactional;

@Service
public class UrlShortenerService {
    private static final Logger log = LoggerFactory.getLogger(UrlShortenerService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final Duration NEGATIVE_CACHE_TTL = Duration.ofSeconds(60);
    private static final String CACHE_KEY_PREFIX = "url:";
    private static final String CACHE_URL_NOT_FOUND_PREFIX = "url:notfound:";
    // one shared breaker instance as a field — state must persist across calls
    private final SimpleCircuitBreaker dbBreaker = SimpleCircuitBreaker.of("url-db-save",
            Set.of(DataIntegrityViolationException.class));

    private final UrlShortenerRepository repository;

    private final PasswordEncoder passwordEncoder;
    private final VisitCountBatcher visitCountBatcher;

    private final RedisTemplate<String, CachedUrl> cachedUrlRedisTemplate;
    private final RedisTemplate<String, Integer> cachedUrlNotFoundRedisTemplate;

    // Self-reference so we can call @Retryable methods THROUGH the Spring proxy.
    // Calling this.saveWithRetry(...) directly would bypass the proxy and skip
    // retry entirely; self.saveWithRetry(...) goes through it. @Lazy breaks the
    // circular dependency of a bean injecting itself.
    private UrlShortenerService self;

    public UrlShortenerService(UrlShortenerRepository repository, PasswordEncoder passwordEncoder,
            RedisTemplate<String, CachedUrl> redisTemplate,
            @Qualifier("cachedUrlNotFoundRedisTemplate") RedisTemplate<String, Integer> redisUrlNotFoundTemplate,
        VisitCountBatcher visitCountBatcher) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.cachedUrlRedisTemplate = redisTemplate;
        this.cachedUrlNotFoundRedisTemplate = redisUrlNotFoundTemplate;
        this.visitCountBatcher = visitCountBatcher;
    }

    @Autowired
    public void setSelf(@Lazy UrlShortenerService self) {
        this.self = self;
    }

    private String cacheKey(String shortCode) {
        return CACHE_KEY_PREFIX + shortCode;
    }

    private String urlNotFoundCache(String shortCode) {
        return CACHE_URL_NOT_FOUND_PREFIX + shortCode;
    }

    private CachedUrl loadCached(String shortCode) {
        String key = cacheKey(shortCode);
        String notFoundKey = urlNotFoundCache(shortCode);

        try {
            CachedUrl cached = (CachedUrl) cachedUrlRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                log.info("Cache hit for shortCode={}", shortCode);
                return cached;
            }

            Integer hit = (Integer) cachedUrlNotFoundRedisTemplate.opsForValue().get(notFoundKey);

            if (hit != null && hit instanceof Integer) {
                log.info("Cache hit for shortCode not found={} hit={}", shortCode, hit);
                cachedUrlNotFoundRedisTemplate.opsForValue().set(notFoundKey, hit + 1, NEGATIVE_CACHE_TTL);
                throw new ShortCodeNotFoundException(shortCode);
            }
        } catch (Exception e) {
            log.warn("Redis cache lookup failed for {}: {}", shortCode, e.getMessage());
        }

        log.debug("Cache miss for shortCode={}", shortCode);

        UrlShortener entity = repository.findByShortCodeAndIsDeletedFalse(shortCode)
                .orElseGet(() -> {
                    try {
                        cachedUrlNotFoundRedisTemplate.opsForValue().set(notFoundKey, 1, NEGATIVE_CACHE_TTL);
                    } catch (Exception ex) {
                        log.warn("Failed to cache negative for {}: {}", shortCode, ex.getMessage());
                    }
                    return null;
                });

        if (entity == null) {
            throw new ShortCodeNotFoundException(shortCode);
        }

        CachedUrl cached = CachedUrl.from(entity);

        try {
            cachedUrlRedisTemplate.opsForValue().set(key, cached, CACHE_TTL);
        } catch (Exception e) {
            log.warn("Failed to cache {}: {}", shortCode, e.getMessage());
        }

        return cached;
    }

    private void evictCache(String shortCode) {
        try {
            cachedUrlRedisTemplate.delete(cacheKey(shortCode));
        } catch (Exception e) {
            log.warn("Failed to evict cache for {}: {}", shortCode, e.getMessage());
        }
    }

    public void evictNotFoundUrlCache(String shortCode) {
        try {
            cachedUrlRedisTemplate.delete(urlNotFoundCache(shortCode));
        } catch (Exception e) {
            log.warn("Failed to evict cache for {}: {}", shortCode, e.getMessage());
        }
    }

    public record ShortenResult(UrlShortener mapping) {
    }

    private CachedUrl loadAndValidate(String shortCode) {
        CachedUrl mapping = loadCached(shortCode);

        if (mapping.expiresAt() != null && mapping.expiresAt().isBefore(Instant.now())) {
            evictCache(shortCode);
            throw new UrlExpiredException(shortCode);
        }

        return mapping;
    }

    private UrlShortener freshLoadAndValidate(String shortCode) {
        UrlShortener entity = repository.findByShortCodeAndIsDeletedFalse(shortCode)
                .orElseThrow(() -> new ShortCodeNotFoundException(shortCode));

        if (entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(Instant.now())) {
            evictCache(shortCode);
            throw new UrlExpiredException(shortCode);
        }

        return entity;
    }

    private void recordVisit(Integer urlId) {
        visitCountBatcher.recordVisit(urlId);
    }

    public ResolveOutcome checkAccess(String shortCode) {
        UrlShortener mapping = freshLoadAndValidate(shortCode);

        if (mapping.getPasswordHash() == null) {
            recordVisit(mapping.getId());
            return new ResolveOutcome(OutcomeType.REDIRECT, mapping.getOriginalUrl());
        }

        return new ResolveOutcome(OutcomeType.PASSWORD_REQUIRED, null);
    }

    @Transactional
    public String resolveWithPassword(String shortCode, String password) {
        UrlShortener entity = freshLoadAndValidate(shortCode);

        if (entity.getPasswordHash() == null) {
            recordVisit(entity.getId());
            return entity.getOriginalUrl();
        }

        if (!passwordEncoder.matches(password, entity.getPasswordHash())) {
            throw new InvalidPasswordException(shortCode);
        }

        recordVisit(entity.getId());
        return entity.getOriginalUrl();
    }

    private String resolveShortCode(String providedShortCode) {
        if (providedShortCode != null) {
            if (repository.findByShortCode(providedShortCode).isPresent()) {
                throw new ShortCodeTakenException(providedShortCode);
            }
            return providedShortCode;
        }

        return generateUniqueCode();
    }

    /**
     * Save a new mapping, retrying only TRANSIENT database faults (connection
     * blips, lock contention). A unique/PK constraint violation is explicitly
     * NOT retried (noRetryFor) — it's deterministic; it propagates so the caller
     * translates it to ShortCodeTakenException.
     *
     * Must be called via self.saveWithRetry(...) so the Spring Retry proxy fires.
     */
    @Retryable(includes = { TransientDataAccessException.class, CannotAcquireLockException.class }, excludes = {
            DataIntegrityViolationException.class }, // constraint conflict: never retry
            maxRetries = 2, // 1 initial + 2 retries = 3 total (matches old maxAttempts=3)
            delay = 150, // ms
            multiplier = 2.0, maxDelay = 1200, jitter = 50 // ms of randomization, replaces random=true
    )
    public UrlShortener saveWithRetry(UrlShortener mapping) {
        return repository.save(mapping);
    }

    public ShortenResult shorten(User user, ShortenRequest request) {
        String providedShortCode = request.shortCode();
        String originalUrl = request.originalUrl();
        Instant expiresAt = request.expiresAt();
        String password = request.password();
        String hashedPassword = (password != null && !password.isBlank()) ? passwordEncoder.encode(password) : null;

        String shortCode = resolveShortCode(providedShortCode);
        String normalized = normalizeUrl(originalUrl);
        UrlShortener mapping = UrlShortener.builder()
                .originalUrl(normalized)
                .shortCode(shortCode)
                .user(user)
                .expiresAt(expiresAt)
                .passwordHash(hashedPassword)
                .build();
        try {
            evictNotFoundUrlCache(shortCode);
            // breaker OUTSIDE, retry INSIDE:
            // self.saveWithRetry does its 3 attempts; only if that whole cycle
            // fails does the breaker count ONE failure. 3 such cycles -> OPEN.
            return new ShortenResult(
                    dbBreaker.execute(() -> self.saveWithRetry(mapping)));
        } catch (DataIntegrityViolationException e) {
            // deterministic conflict — not retried, translated instead
            throw new ShortCodeTakenException(shortCode);
        } catch (SimpleCircuitBreaker.CircuitOpenException e) {
            // breaker is OPEN — DB is presumed down, fail fast without waiting
            log.warn("DB circuit open, rejecting shorten for {}", shortCode);
            throw new ServiceUnavailableException("Service temporarily unavailable, try again shortly");
        }
    }

    @Transactional
    public ShortenResult edit(User user, Instant expiresAt, String shortCode) {
        UrlShortener mapping = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortCodeNotFoundException(shortCode));
        if (!mapping.getUser().equals(user)) {
            throw new ForbiddenException("You don't own this short code");
        }

        if (expiresAt != null) {
            mapping.setExpiresAt(expiresAt);
        }
        evictCache(shortCode);
        return new ShortenResult(repository.save(mapping));

    }

    public BulkShortenResponse bulkShorten(User user, List<ShortenRequest> urls) {
        List<UnitShortenResponse> unitResponses = new ArrayList<>();

        for (int i = 0; i < urls.size(); i++) {
            ShortenRequest shortenRequest = urls.get(i);
            try {
                ShortenResult result = shorten(user, shortenRequest);

                unitResponses.add(UnitShortenResponse.success(i, result.mapping));
            } catch (ShortCodeTakenException e) {
                unitResponses.add(UnitShortenResponse.failure(i,
                        shortenRequest.originalUrl(), "Short code already taken"));
            } catch (Exception e) {
                unitResponses.add(UnitShortenResponse.failure(i,
                        shortenRequest.originalUrl(), "Internal error processing this URL"));
            }
        }

        return BulkShortenResponse.from(unitResponses);
    }

    private String normalizeUrl(String url) {
        return url.trim().toLowerCase();
    }

    @Transactional
    public String resolve(String shortCode) {
        CachedUrl mapping = loadAndValidate(shortCode);

        if (mapping.hasPassword()) {
            throw new PasswordRequiredException(shortCode);
        }

        recordVisit(mapping.id());
        return mapping.originalUrl();
    }

    public Page<ShortenResponse> listUrls(User user, Pageable pageable, boolean includeDeleted) {
        Page<UrlShortener> entities;
        if (includeDeleted) {
            entities = repository.findByUserId(user.getId(), pageable);
        } else {
            entities = repository.findByUserIdAndIsDeletedFalse(user.getId(), pageable);
        }
        return entities.map(ShortenResponse::from);
    }

    public UrlShortener findByShortCode(String shortCode) {
        return repository.findByShortCode(shortCode).orElse(null);
    }

    @Transactional
    public void deleteShortCode(String shortCode, Integer userId) {
        UrlShortener mapping = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortCodeNotFoundException(shortCode));

        if (!userId.equals(mapping.getUser().getId())) {
            throw new ForbiddenException("You don't own this short code");
        }

        if (Boolean.TRUE.equals(mapping.getIsDeleted())) {
            throw new ShortCodeNotFoundException(shortCode);
        }

        mapping.setIsDeleted(true);
        mapping.setDeletedAt(LocalDateTime.now());
        evictCache(shortCode);
        repository.save(mapping);
    }

    private String generateUniqueCode() {
        UUID uuid = UUID.randomUUID();
        String uuidString = uuid.toString().replace("-", "").substring(0, 10);
        return uuidString;
    }
}