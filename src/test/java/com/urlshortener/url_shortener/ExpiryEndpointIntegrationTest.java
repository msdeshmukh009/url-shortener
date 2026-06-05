package com.urlshortener.url_shortener;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.urlshortener.url_shortener.entity.Tier;
import com.urlshortener.url_shortener.entity.UrlShortener;
import com.urlshortener.url_shortener.entity.User;
import com.urlshortener.url_shortener.enums.TierType;
import com.urlshortener.url_shortener.repository.TierRepository;
import com.urlshortener.url_shortener.repository.UrlShortenerRepository;
import com.urlshortener.url_shortener.repository.UserRepository;

import tools.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SpringBootTest
@AutoConfigureMockMvc
public class ExpiryEndpointIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UrlShortenerRepository urlRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    TierRepository tierRepository;

    private User testUser;

    @BeforeEach
    void setup() {
        Tier hobbyTier = tierRepository.findByName(TierType.HOBBY)
                .orElseGet(
                        () -> tierRepository.save(
                                Tier.builder()
                                        .name(TierType.HOBBY)
                                        .canUseBulkCreation(false)
                                        .build()));
        testUser = userRepository.save(User.builder()
                .email("test-" + UUID.randomUUID() + "@test.com")
                .name("Test User")
                .tier(hobbyTier)
                .apiKey("apikey-" + UUID.randomUUID())
                .build());
    }

    private String createShortCodeWithExpiry(Instant expiresAt) throws Exception {
        String url = "https://example.com/" + UUID.randomUUID();
        String body;

        if (expiresAt != null) {
            body = """
                    {
                        "originalUrl": "%s",
                        "expiresAt": "%s"
                    }
                    """.formatted(url, expiresAt);
        } else {
            body = """
                    {
                        "originalUrl": "%s"
                    }
                    """.formatted(url);
        }

        MvcResult result = mockMvc.perform(post("/api/shorten")
                .header("X-API-Key", testUser.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("shortCode").asString();
    }

    @Test
    void shouldCreateUrlWithoutExpiry() throws Exception {
        String shortCode = createShortCodeWithExpiry(null);

        UrlShortener row = urlRepository.findByShortCode(shortCode).orElseThrow();
        assertThat(row.getExpiresAt()).isNull();

        mockMvc.perform(get("/api/redirect").param("shortCode", shortCode))
                .andExpect(status().isFound());
    }

    @Test
    void shouldResolveUrlBeforeExpiry() throws Exception {
        Instant futureExpiry = Instant.now().plus(7, ChronoUnit.DAYS);
        String shortCode = createShortCodeWithExpiry(futureExpiry);

        UrlShortener row = urlRepository.findByShortCode(shortCode).orElseThrow();
        assertThat(row.getExpiresAt()).isNotNull();
        assertThat(row.getExpiresAt()).isAfter(Instant.now());

        mockMvc.perform(get("/api/redirect").param("shortCode", shortCode))
                .andExpect(status().isFound());
    }

    @Test
    void shouldRejectCreationWithPastExpiry() throws Exception {
        String url = "https://example.com/" + UUID.randomUUID();
        String body = """
                {
                    "originalUrl": "%s",
                    "expiresAt": "%s"
                }
                """.formatted(url, Instant.now().minus(1, ChronoUnit.DAYS));

        mockMvc.perform(post("/api/shorten")
                .header("X-API-Key", testUser.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Expiry must be in the future")));
    }

    @Test
    void shouldReturn410ForExpiredUrl() throws Exception {
        String shortCode = createShortCodeWithExpiry(Instant.now().plus(1, ChronoUnit.HOURS));

        UrlShortener row = urlRepository.findByShortCode(shortCode).orElseThrow();
        row.setExpiresAt(Instant.now().minusSeconds(60));
        urlRepository.save(row);

        mockMvc.perform(get("/api/redirect").param("shortCode", shortCode))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value("410 GONE"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("expired")));
    }

    @Test
    void shouldNotIncrementVisitCountForExpiredUrl() throws Exception {
        String shortCode = createShortCodeWithExpiry(Instant.now().plus(1, ChronoUnit.HOURS));
        UrlShortener row = urlRepository.findByShortCode(shortCode).orElseThrow();
        row.setExpiresAt(Instant.now().minusSeconds(60));
        urlRepository.save(row);

        Integer countBefore = row.getVisitCount();

        mockMvc.perform(get("/api/redirect").param("shortCode", shortCode))
                .andExpect(status().isGone());

        UrlShortener after = urlRepository.findByShortCode(shortCode).orElseThrow();
        assertThat(after.getVisitCount()).isEqualTo(countBefore);
    }

    @Test
    void shouldIncludeExpiresAtInResponse() throws Exception {
        Instant expiry = Instant.now().plus(30, ChronoUnit.DAYS);
        String url = "https://example.com/" + UUID.randomUUID();
        String body = """
                {
                    "originalUrl": "%s",
                    "expiresAt": "%s"
                }
                """.formatted(url, expiry);

        mockMvc.perform(post("/api/shorten")
                .header("X-API-Key", testUser.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void shouldReturnNullExpiresAtWhenNotSet() throws Exception {
        String url = "https://example.com/" + UUID.randomUUID();
        String body = """
                {
                    "originalUrl": "%s"
                }
                """.formatted(url);

        mockMvc.perform(post("/api/shorten")
                .header("X-API-Key", testUser.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").isEmpty());
    }
}