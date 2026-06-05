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

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SpringBootTest
@AutoConfigureMockMvc
public class DeleteEndpointIntegrationTest {

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
    void shouldAllowOwnerToDelete() throws Exception {
        String shortCode = createShortCode(userA);

        mockMvc.perform(delete("/api/urls/" + shortCode)
                .header("X-API-Key", userA.getApiKey()))
                .andExpect(status().isNoContent());

        UrlShortener row = urlRepository.findByShortCode(shortCode).orElseThrow();
        assertThat(row.getIsDeleted()).isTrue();
        assertThat(row.getDeletedAt()).isNotNull();
    }

    @Test
    void shouldReject403WhenNonOwnerTriesToDelete() throws Exception {
        String shortCode = createShortCode(userA); 

        mockMvc.perform(delete("/api/urls/" + shortCode)
                .header("X-API-Key", userB.getApiKey()))
                .andExpect(status().isForbidden());

        UrlShortener row = urlRepository.findByShortCode(shortCode).orElseThrow();
        assertThat(row.getIsDeleted()).isFalse();
    }

    @Test
    void shouldReject400WhenApiKeyMissing() throws Exception {
        String shortCode = createShortCode(userA);

        mockMvc.perform(delete("/api/urls/" + shortCode))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReject401WhenApiKeyInvalid() throws Exception {
        String shortCode = createShortCode(userA);

        mockMvc.perform(delete("/api/urls/" + shortCode)
                .header("X-API-Key", "definitely-not-a-real-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn404ForNonExistentShortCode() throws Exception {
        mockMvc.perform(delete("/api/urls/doesnotexist")
                .header("X-API-Key", userA.getApiKey()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn404WhenDeletingAlreadyDeletedCode() throws Exception {
        String shortCode = createShortCode(userA);

        mockMvc.perform(delete("/api/urls/" + shortCode)
                .header("X-API-Key", userA.getApiKey()))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/urls/" + shortCode)
                .header("X-API-Key", userA.getApiKey()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn404WhenResolvingDeletedShortCode() throws Exception {
        String shortCode = createShortCode(userA);

        mockMvc.perform(delete("/api/urls/" + shortCode)
                .header("X-API-Key", userA.getApiKey()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/redirect").param("shortCode", shortCode))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldKeepRowInDatabaseAfterDelete() throws Exception {
        String shortCode = createShortCode(userA);

        mockMvc.perform(delete("/api/urls/" + shortCode)
                .header("X-API-Key", userA.getApiKey()))
                .andExpect(status().isNoContent());

        assertThat(urlRepository.findByShortCode(shortCode)).isPresent();
    }
}
