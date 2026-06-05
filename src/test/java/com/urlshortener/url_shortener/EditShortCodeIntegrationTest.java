package com.urlshortener.url_shortener;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.urlshortener.url_shortener.entity.Tier;
import com.urlshortener.url_shortener.entity.User;
import com.urlshortener.url_shortener.enums.TierType;
import com.urlshortener.url_shortener.repository.TierRepository;
import com.urlshortener.url_shortener.repository.UserRepository;

import tools.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SpringBootTest
@AutoConfigureMockMvc
public class EditShortCodeIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    TierRepository tierRepository;

    private User userA;
    private User userB;

    @BeforeEach
    void setup() {
        Tier hobbyTier = tierRepository.findByName(TierType.HOBBY)
                .orElseGet(
                        () -> tierRepository.save(
                                Tier.builder()
                                        .name(TierType.HOBBY)
                                        .canUseBulkCreation(false)
                                        .build()));

        userA = userRepository.save(User.builder()
                .email("a-" + UUID.randomUUID() + "@test.com")
                .name("User A")
                .tier(hobbyTier)
                .apiKey("apikey-A-" + UUID.randomUUID())
                .build());

        userB = userRepository.save(User.builder()
                .email("b-" + UUID.randomUUID() + "@test.com")
                .name("User B")
                .tier(hobbyTier)
                .apiKey("apikey-B-" + UUID.randomUUID())
                .build());
    }

    private String createShortCode(User owner) throws Exception {
        String body = """
                { "originalUrl": "https://example.com/%s" }
                """.formatted(UUID.randomUUID());

        MvcResult result = mockMvc.perform(post("/api/shorten")
                .header("X-API-Key", owner.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("shortCode").asString();
    }

    @Test
    void shouldAllowOwnerToEdit() throws Exception {
        String shortCode = createShortCode(userA);
        Instant pastExpiry = Instant.now().minus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

        String body = """
                { "expiresAt": "%s" }
                """.formatted(pastExpiry);

        mockMvc.perform(patch("/api/shorten/" + shortCode)
                .header("X-API-Key", userA.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/redirect").param("shortCode", shortCode))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Short code has expired")));
    }

    @Test
    void shouldReject403WhenNonOwnerTriesToEdit() throws Exception {
        String shortCode = createShortCode(userA);

        Instant pastExpiry = Instant.now().minus(7, ChronoUnit.DAYS);

        String body = """
                { "expiresAt": "%s" }
                """.formatted(pastExpiry);

        mockMvc.perform(patch("/api/shorten/" + shortCode)
                .header("X-API-Key", userB.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReject400WhenApiKeyMissing() throws Exception {
        String shortCode = createShortCode(userA);

        Instant pastExpiry = Instant.now().minus(7, ChronoUnit.DAYS);

        String body = """
                { "expiresAt": "%s" }
                """.formatted(pastExpiry);

        mockMvc.perform(patch("/api/shorten/" + shortCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReject401WhenApiKeyInvalid() throws Exception {
        String shortCode = createShortCode(userA);

        Instant pastExpiry = Instant.now().minus(7, ChronoUnit.DAYS);

        String body = """
                { "expiresAt": "%s" }
                """.formatted(pastExpiry);

        mockMvc.perform(patch("/api/shorten/" + shortCode)
                .header("X-API-Key", "some-random-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn404ForNonExistentShortCode() throws Exception {
        Instant pastExpiry = Instant.now().minus(7, ChronoUnit.DAYS);

        String body = """
                { "expiresAt": "%s" }
                """.formatted(pastExpiry);

        mockMvc.perform(patch("/api/shorten/" + "this-shortcode-is-short")
                .header("X-API-Key", userA.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isNotFound());
    }
}
