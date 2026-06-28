package com.urlshortener.url_shortener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.urlshortener.url_shortener.entity.Tier;
import com.urlshortener.url_shortener.entity.UrlShortener;
import com.urlshortener.url_shortener.entity.User;
import com.urlshortener.url_shortener.enums.TierType;
import com.urlshortener.url_shortener.repository.TierRepository;
import com.urlshortener.url_shortener.repository.UserRepository;
import com.urlshortener.url_shortener.utils.UrlUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "ratelimit.max-requests-per-min=100",
        "ratelimit.max-redirect-requests-per-min=50",
        "ratelimit.max-shorten-requests-per-min:10"
})
@AutoConfigureMockMvc
public class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TierRepository tierRepository;

    @Autowired
    private RedisTemplate<String, Long> redisTemplate;

    private User user;
    private User freeTierUser;
    private static final String TEST_IP = "203.0.113.77";
    private static final String TEST_IP_1 = "192.0.2.1";
    private static final String TEST_IP_2 = "192.0.2.2";
    private static final String RATE_KEY = "ratelimit:ip:" + TEST_IP;
    private static final String RATE_KEY_1 = "ratelimit:ip:" + TEST_IP_1;
    private static String API_KEY_SHORTEN_PREFIX;
    private static String FREE_API_KEY_SHORTEN_PREFIX;

    @BeforeEach
    void setup() {
        Tier hobbyTier = tierRepository.findByName(TierType.HOBBY)
                .orElseGet(() -> tierRepository.save(
                        Tier.builder()
                                .name(TierType.HOBBY)
                                .canUseBulkCreation(false)
                                .build()));
         Tier freeTier = tierRepository.findByName(TierType.FREE)
                .orElseGet(() -> tierRepository.save(
                        Tier.builder()
                                .name(TierType.FREE)
                                .canUseBulkCreation(false)
                                .build()));
        String apiKey = "apikey-rate-" + UUID.randomUUID();
        String apiKey2 = "apikey-rate-" + UUID.randomUUID();

        freeTierUser =  userRepository.save(User.builder()
                .email("rate-" + UUID.randomUUID() + "@test.com")
                .name("Rate User")
                .tier(freeTier)
                .apiKey(apiKey2)
                .build());

        user = userRepository.save(User.builder()
                .email("rate-" + UUID.randomUUID() + "@test.com")
                .name("Rate User")
                .tier(hobbyTier)
                .apiKey(apiKey)
                .build());

        API_KEY_SHORTEN_PREFIX = "ratelimit:apiKey:shorten" + apiKey;
        FREE_API_KEY_SHORTEN_PREFIX = "ratelimit:apiKey:shorten" + apiKey2;

        // clear any leftover counter so the test is deterministic
        redisTemplate.delete(RATE_KEY);
        redisTemplate.delete(RATE_KEY_1);
        redisTemplate.delete(API_KEY_SHORTEN_PREFIX);
        redisTemplate.delete(FREE_API_KEY_SHORTEN_PREFIX);
    }

    @Test
    void shouldThrottleShortenAfterExceedingLimit() throws Exception {
        String requestBody = """
                {
                    "originalUrl": "%s"
                }
                """.formatted(UrlUtils.generateRandomUrl());

        // First 10 requests should pass (not 429)
        for (int i = 0; i < 10; i++) {
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

        // 11th request should be throttled
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

    @Test
    void shouldThrottleRedirectAfterExceedingLimit() throws Exception {
        String url = UrlUtils.generateRandomUrl();
        String requestBody = """
                {
                    "originalUrl": "%s"
                }
                """.formatted(url);

        MvcResult shortenResult = mockMvc.perform(post("/api/shorten")
                .header("X-API-Key", user.getApiKey())
                .header("X-Forwarded-For", TEST_IP_1)
                .with(req -> {
                    req.setRemoteAddr(TEST_IP_1);
                    return req;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated()).andReturn();

        String responseJson = shortenResult.getResponse().getContentAsString();
        UrlShortener saved = objectMapper.readValue(responseJson, UrlShortener.class);
        String shortCode = saved.getShortCode();

        // First 50 requests should pass (not 429)
        for (int i = 0; i < 50; i++) {
            mockMvc.perform(
                    get("/api/redirect")
                    .header("X-Forwarded-For", TEST_IP_1)
                            .param("shortCode", shortCode))
                    .andExpect(status().isFound()) // expect 302 redirect
                    .andExpect(header().string("Location", url));
        }
        // 51st request should be throttled
        mockMvc.perform(
                get("/api/redirect")
                .header("X-Forwarded-For", TEST_IP_1)
                        .param("shortCode", shortCode))
                .andExpect(status().isTooManyRequests()); // too many requests
    }

    @Test
    void shouldThrottleAfterExceedingLimit() throws Exception {
        // First 100 requests should pass (not 429)
        for (int i = 0; i < 100; i++) {
            mockMvc.perform(delete("/api/urls/doesnotexist")
                    .header("X-API-Key", user.getApiKey())
                    .header("X-Forwarded-For", TEST_IP)
                    .with(req -> {
                        req.setRemoteAddr(TEST_IP);
                        return req;
                    }))
                    .andExpect(status().isNotFound());
        }
        // 101st request should be throttled
        mockMvc.perform(delete("/api/urls/doesnotexist")
                .header("X-API-Key", user.getApiKey())
                .header("X-Forwarded-For", TEST_IP)
                .with(req -> {
                    req.setRemoteAddr(TEST_IP);
                    return req;
                }))
                .andExpect(status().isTooManyRequests()); // too many requests
    }

    
    @Test
    void shouldThrottleShortenOnFreeTierAfterExceedingLimit() throws Exception {
        String requestBody = """
                {
                    "originalUrl": "%s"
                }
                """.formatted(UrlUtils.generateRandomUrl());

        // First 5 requests should pass (not 429)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/shorten")
                    .header("X-API-Key", freeTierUser.getApiKey())
                    .header("X-Forwarded-For", TEST_IP_2)
                    .with(req -> {
                        req.setRemoteAddr(TEST_IP_2);
                        return req;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                    .andExpect(status().isCreated());
        }

        // 6th request should be throttled
        mockMvc.perform(post("/api/shorten")
                .header("X-API-Key", freeTierUser.getApiKey())
                .header("X-Forwarded-For", TEST_IP_2)
                .with(req -> {
                    req.setRemoteAddr(TEST_IP_2);
                    return req;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isTooManyRequests());
    }
}