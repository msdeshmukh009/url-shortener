package com.urlshortener.url_shortener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.urlshortener.url_shortener.entity.Tier;
import com.urlshortener.url_shortener.entity.User;
import com.urlshortener.url_shortener.enums.TierType;
import com.urlshortener.url_shortener.repository.TierRepository;
import com.urlshortener.url_shortener.repository.UserRepository;
import com.urlshortener.url_shortener.utils.UrlUtils;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "ratelimit.max-requests-per-min=100")
@AutoConfigureMockMvc
public class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TierRepository tierRepository;

    @Autowired
    private RedisTemplate<String, Long> redisTemplate;

    private User user;
    private static final String TEST_IP = "203.0.113.77";
    private static final String RATE_KEY = "ratelimit:ip:" + TEST_IP;

    @BeforeEach
    void setup() {
        Tier hobbyTier = tierRepository.findByName(TierType.HOBBY)
                .orElseGet(() -> tierRepository.save(
                        Tier.builder()
                                .name(TierType.HOBBY)
                                .canUseBulkCreation(false)
                                .build()));

        user = userRepository.save(User.builder()
                .email("rate-" + UUID.randomUUID() + "@test.com")
                .name("Rate User")
                .tier(hobbyTier)
                .apiKey("apikey-rate-" + UUID.randomUUID())
                .build());

        // clear any leftover counter so the test is deterministic
        redisTemplate.delete(RATE_KEY);
    }

    @Test
    void shouldThrottleAfterExceedingLimit() throws Exception {
        String requestBody = """
                {
                    "originalUrl": "%s"
                }
                """.formatted(UrlUtils.generateRandomUrl());

        // First 100 requests should pass (not 429)
        for (int i = 0; i < 100; i++) {
            mockMvc.perform(post("/api/shorten")
                    .header("X-API-Key", user.getApiKey())
                    .header("X-Forwarded-For", TEST_IP)
                    .with(req -> {
                        req.setRemoteAddr(TEST_IP);
                        return req;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                    .andExpect(status().isCreated());
        }

        // 101st request should be throttled
        mockMvc.perform(post("/api/shorten")
                .header("X-API-Key", user.getApiKey())
                .header("X-Forwarded-For", TEST_IP)
                .with(req -> {
                    req.setRemoteAddr(TEST_IP);
                    return req;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isTooManyRequests());
    }
}