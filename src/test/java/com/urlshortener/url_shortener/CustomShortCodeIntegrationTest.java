package com.urlshortener.url_shortener;

import com.urlshortener.url_shortener.repository.*;

import tools.jackson.databind.ObjectMapper;

import com.urlshortener.url_shortener.entity.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
public class CustomShortCodeIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UrlShortenerRepository urlRepository;

    @Autowired
    UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setup() {
        testUser = userRepository.save(User.builder()
                .email("test-" + UUID.randomUUID() + "@test.com")
                .name("User Test")
                .apiKey("apikey-t-" + UUID.randomUUID())
                .build());

    }

    public static String generateShortCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    }

    @Test
    void shouldCreateUrlWithCustomShortCode() throws Exception {
        String body = """
                { "originalUrl": "https://example.com/%s",
                    "shortCode": "test-custom-code-%s"
                }
                """.formatted(UUID.randomUUID(), generateShortCode());

        MvcResult result = mockMvc.perform(post("/api/shorten")
                .header("X-API-Key", testUser.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String responseShortCode = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("shortCode").asString();

        assertThat(responseShortCode).startsWith("test-custom-code-");
    }

    @Test
    void shouldReturn409WhenCustomShortCodeAlreadyExists() throws Exception {
        String customCode = "duplicate-code-" + generateShortCode();

        // create first time — succeeds
        String body1 = """
                {
                    "originalUrl": "https://example.com/first",
                    "shortCode": "%s"
                }
                """.formatted(customCode);
        mockMvc.perform(post("/api/shorten")
                .header("X-API-Key", testUser.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body1))
                .andExpect(status().isCreated());

        // try same code again — fails with 409
        String body2 = """
                {
                    "originalUrl": "https://example.com/second",
                    "shortCode": "%s"
                }
                """.formatted(customCode);
        mockMvc.perform(post("/api/shorten")
                .header("X-API-Key", testUser.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("already taken")));
    }

    @Test
    void shouldReject400ForShortCodeWithInvalidCharacters() throws Exception {
        String body = """
                {
                    "originalUrl": "https://example.com/test",
                    "shortCode": "has spaces and slashes/here"
                }
                """;

        mockMvc.perform(post("/api/shorten")
                .header("X-API-Key", testUser.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("can only contain letters")));
    }

    @Test
    void shouldReject400ForShortCodeThatIsTooShort() throws Exception {
        String body = """
                {
                    "originalUrl": "https://example.com/test",
                    "shortCode": "ab"
                }
                """;

        mockMvc.perform(post("/api/shorten")
                .header("X-API-Key", testUser.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("3-50 characters")));
    }

    @Test
    void shouldReject400ForShortCodeWithPathTraversal() throws Exception {
        String body = """
                {
                    "originalUrl": "https://example.com/test",
                    "shortCode": "../../../etc/passwd"
                }
                """;

        mockMvc.perform(post("/api/shorten")
                .header("X-API-Key", testUser.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRedirectCustomShortCode() throws Exception {
        String customCode = "redirect-test-" + UUID.randomUUID().toString().substring(0, 8);
        String body = """
                {
                    "originalUrl": "https://example.com/final-destination",
                    "shortCode": "%s"
                }
                """.formatted(customCode);

        mockMvc.perform(post("/api/shorten")
                .header("X-API-Key", testUser.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/redirect").param("shortCode", customCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/final-destination"));
    }
}
