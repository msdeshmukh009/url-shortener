package com.urlshortener.url_shortener;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;

import com.urlshortener.url_shortener.entity.Tier;
import com.urlshortener.url_shortener.entity.User;
import com.urlshortener.url_shortener.enums.TierType;
import com.urlshortener.url_shortener.repository.TierRepository;
import com.urlshortener.url_shortener.repository.UrlShortenerRepository;
import com.urlshortener.url_shortener.repository.UserRepository;
import static org.hamcrest.Matchers.hasSize;

@SpringBootTest
@AutoConfigureMockMvc
public class BulkShortenEndpointIntegrationTest {
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

    private User tesEnterpriseUser;

    private User testHobbyUser;

    @BeforeEach
    void setup() {
        Tier enterpriseTier = tierRepository.findByName(TierType.ENTERPRISE)
                .orElseGet(
                        () -> tierRepository.save(
                                Tier.builder()
                                        .name(TierType.ENTERPRISE)
                                        .canUseBulkCreation(false)
                                        .build()));

        Tier hobbyTier = tierRepository.findByName(TierType.HOBBY)
                .orElseGet(
                        () -> tierRepository.save(
                                Tier.builder()
                                        .name(TierType.HOBBY)
                                        .canUseBulkCreation(false)
                                        .build()));

        tesEnterpriseUser = userRepository.save(User.builder()
                .email("bulk-test-" + UUID.randomUUID() + "@test.com")
                .name("Bulk Test User")
                .tier(enterpriseTier)
                .apiKey("bulk-key-" + UUID.randomUUID())
                .build());

        testHobbyUser = userRepository.save(User.builder()
                .email("bulk-test-" + UUID.randomUUID() + "@test.com")
                .name("Bulk Test User")
                .tier(hobbyTier)
                .apiKey("bulk-key-" + UUID.randomUUID())
                .build());
    }

    private String buildBulkRequest(String... urls) {
        StringBuilder sb = new StringBuilder("{ \"urls\": [");
        for (int i = 0; i < urls.length; i++) {
            if (i > 0)
                sb.append(", ");
            sb.append("{ \"originalUrl\": \"").append(urls[i]).append("\" }");
        }
        sb.append("] }");
        return sb.toString();
    }

    @Test
    void shouldReturn403WhenHobbyUserTriesBulkShorten() throws Exception {
        String body = buildBulkRequest(
                "https://example.com/" + UUID.randomUUID(),
                "https://another.com/" + UUID.randomUUID(),
                "https://third.com/" + UUID.randomUUID());

        mockMvc.perform(
                post("/api/shorten/bulk")
                        .header("X-API-Key", testHobbyUser.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("403 FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("This endpoint requires the ENTERPRISE tier")));
    }

    @Test
    void shouldReturn201WhenAllUrlsSucceed() throws Exception {
        String body = buildBulkRequest(
                "https://example.com/" + UUID.randomUUID(),
                "https://another.com/" + UUID.randomUUID(),
                "https://third.com/" + UUID.randomUUID());

        mockMvc.perform(
                post("/api/shorten/bulk")
                        .header("X-API-Key", tesEnterpriseUser.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.succeeded").value(3))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.result", hasSize(3)))
                .andExpect(jsonPath("$.result[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.result[0].shortCode").exists());
        ;
    }

    @Test
    void shouldPersistAllSuccessfulShortenings() throws Exception {
        String body = buildBulkRequest(
                "https://saved.com/" + UUID.randomUUID(),
                "https://saved.com/" + UUID.randomUUID());

        var result = mockMvc.perform(post("/api/shorten/bulk")
                .header("X-API-Key", tesEnterpriseUser.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        var responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
        for (var node : responseJson.get("result")) {
            String shortCode = node.get("shortCode").asString();
            assertThat(urlRepository.findByShortCode(shortCode)).isPresent();
        }
    }

    @Test
    void shouldReturn207WhenSomeUrlsFail() throws Exception {
        String takenCode = "taken-" + UUID.randomUUID().toString().substring(0, 8);

        String firstBody = """
                {
                    "originalUrl": "https://first.com/test",
                    "shortCode": "%s"
                }
                """.formatted(takenCode);
        mockMvc.perform(post("/api/shorten")
                .header("X-API-Key", tesEnterpriseUser.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstBody))
                .andExpect(status().isCreated());

        String bulkBody = """
                {
                    "urls": [
                        { "originalUrl": "https://good1.com/%s" },
                        { "originalUrl": "https://collide.com", "shortCode": "%s" },
                        { "originalUrl": "https://good2.com/%s" }
                    ]
                }
                """.formatted(UUID.randomUUID(), takenCode, UUID.randomUUID());

        mockMvc.perform(post("/api/shorten/bulk")
                .header("X-API-Key", tesEnterpriseUser.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(bulkBody))
                .andExpect(status().isMultiStatus()) // 207
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.succeeded").value(2))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.result[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.result[1].status").value("FAILED"))
                .andExpect(jsonPath("$.result[1].error").exists())
                .andExpect(jsonPath("$.result[2].status").value("SUCCESS"));
    }

    @Test
    void shouldReject400ForBatchSizeOverLimit() throws Exception {
        StringBuilder sb = new StringBuilder("{ \"urls\": [");
        for (int i = 0; i < 101; i++) {
            if (i > 0)
                sb.append(", ");
            sb.append("{ \"originalUrl\": \"https://example.com/").append(i).append("\" }");
        }
        sb.append("] }");

        mockMvc.perform(post("/api/shorten/bulk")
                .header("X-API-Key", tesEnterpriseUser.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(sb.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReject400IfAnyUrlInBatchIsInvalid() throws Exception {
        String body = """
                {
                    "urls": [
                        { "originalUrl": "https://valid.com/%s" },
                        { "originalUrl": "not-a-valid-url" },
                        { "originalUrl": "https://also-valid.com/%s" }
                    ]
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/shorten/bulk")
                .header("X-API-Key", tesEnterpriseUser.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());
    }
}
