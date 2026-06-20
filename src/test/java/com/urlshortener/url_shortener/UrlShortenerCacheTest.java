package com.urlshortener.url_shortener;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.urlshortener.url_shortener.entity.Tier;
import com.urlshortener.url_shortener.entity.User;
import com.urlshortener.url_shortener.enums.TierType;
import com.urlshortener.url_shortener.repository.TierRepository;
import com.urlshortener.url_shortener.repository.UrlShortenerRepository;
import com.urlshortener.url_shortener.repository.UserRepository;
import com.urlshortener.url_shortener.service.UrlShortenerService;

import jakarta.transaction.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class UrlShortenerCacheTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    UrlShortenerService service;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    TierRepository tierRepository;

    @MockitoSpyBean
    UrlShortenerRepository repository;

    private User testUser;
    private String shortCode;

    @BeforeEach
    void setup() throws Exception {
        Tier hobbyTier = tierRepository.findByName(TierType.HOBBY)
                .orElseGet(() -> tierRepository.save(
                        Tier.builder()
                                .name(TierType.HOBBY)
                                .canUseBulkCreation(false)
                                .build()));

        testUser = userRepository.save(User.builder()
                .email("redirect-test-" + UUID.randomUUID() + "@test.com")
                .name("Redirect Test User")
                .apiKey("redirect-key-" + UUID.randomUUID())
                .tier(hobbyTier)
                .build());

        MvcResult result = mockMvc.perform(post("/api/shorten")
                .header("X-API-Key", testUser.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"originalUrl": "https://example.com/test"}
                        """))
                .andExpect(status().isCreated())
                .andReturn();

        shortCode = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("shortCode").asString();

        Mockito.reset(repository);
    }

    @Test
    void firstResolveShouldHitDatabase() throws Exception {
        mockMvc.perform(get("/api/redirect").param("shortCode", shortCode))
                .andExpect(status().isFound());

        verify(repository, times(1)).findByShortCodeAndIsDeletedFalse(shortCode);
    }

    @Test
    void secondResolveShouldUseCacheAndNotHitDatabase() throws Exception {
        // First call — populates cache
        mockMvc.perform(get("/api/redirect").param("shortCode", shortCode))
                .andExpect(status().isFound());

        // Reset the verification counter
        Mockito.reset(repository);

        // Second call — should be cached, no DB hit
        mockMvc.perform(get("/api/redirect").param("shortCode", shortCode))
                .andExpect(status().isFound());

        // Verify the findByShortCode was NOT called this time
        verify(repository, never()).findByShortCodeAndIsDeletedFalse(shortCode);
    }

    @Test
    void multipleConsecutiveCallsShouldOnlyHitDatabaseOnce() throws Exception {
        // Hit the endpoint 10 times
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/redirect").param("shortCode", shortCode))
                    .andExpect(status().isFound());
        }

        // DB should only have been queried once (first time)
        verify(repository, times(1)).findByShortCodeAndIsDeletedFalse(shortCode);
    }
}
