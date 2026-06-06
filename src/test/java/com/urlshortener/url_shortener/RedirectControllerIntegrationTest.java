package com.urlshortener.url_shortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;
import com.urlshortener.url_shortener.entity.Tier;
import com.urlshortener.url_shortener.entity.UrlShortener;
import com.urlshortener.url_shortener.entity.User;
import com.urlshortener.url_shortener.enums.TierType;
import com.urlshortener.url_shortener.repository.TierRepository;
import com.urlshortener.url_shortener.repository.UrlShortenerRepository;
import com.urlshortener.url_shortener.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class RedirectControllerIntegrationTest {

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
    private static final String CORRECT_PASSWORD = "mySecretPassword123";

    @BeforeEach
    void setup() {
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
    }

    private String createShortUrl(String originalUrl, String password) throws Exception {
        String body;
        if (password != null) {
            body = """
                    {
                        "originalUrl": "%s",
                        "password": "%s"
                    }
                    """.formatted(originalUrl, password);
        } else {
            body = """
                    {
                        "originalUrl": "%s"
                    }
                    """.formatted(originalUrl);
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

    private String createExpiredUrl(String originalUrl, String password) throws Exception {
        String shortCode = createShortUrl(originalUrl, password);
        UrlShortener row = urlRepository.findByShortCode(shortCode).orElseThrow();
        row.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        urlRepository.saveAndFlush(row);
        return shortCode;
    }

    @Test
    void shouldRenderFormForPasswordProtectedUrl() throws Exception {
        String shortCode = createShortUrl(
                "https://example.com/protected-" + UUID.randomUUID(),
                CORRECT_PASSWORD);

        mockMvc.perform(get("/r/" + shortCode))
                .andExpect(status().isOk())
                .andExpect(view().name("password-form"))
                .andExpect(model().attribute("shortCode", shortCode))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Password Required")))
                .andExpect(content().string(containsString(
                        "action=\"/r/" + shortCode + "/unlock\"")))
                .andExpect(content().string(containsString("type=\"password\"")));
    }

    @Test
    void shouldRedirectImmediatelyForUnprotectedUrl() throws Exception {
        String targetUrl = "https://example.com/unprotected-" + UUID.randomUUID();
        String shortCode = createShortUrl(targetUrl, null);

        mockMvc.perform(get("/r/" + shortCode))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(targetUrl));
    }

    @Test
    void shouldReturn404ForNonExistentShortCode() throws Exception {
        mockMvc.perform(get("/r/definitely-not-a-real-code-" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn404ForDeletedShortCode() throws Exception {
        String shortCode = createShortUrl(
                "https://example.com/will-be-deleted-" + UUID.randomUUID(),
                null);

        mockMvc.perform(delete("/api/urls/" + shortCode)
                .header("X-API-Key", testUser.getApiKey()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/r/" + shortCode))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn410ForExpiredUrl() throws Exception {
        String shortCode = createExpiredUrl(
                "https://example.com/expired-" + UUID.randomUUID(),
                null);

        mockMvc.perform(get("/r/" + shortCode))
                .andExpect(status().isGone());
    }

    @Test
    void shouldReturn410ForExpiredPasswordProtectedUrl_evenIfPasswordCorrect() throws Exception {
        String shortCode = createExpiredUrl(
                "https://example.com/expired-protected-" + UUID.randomUUID(),
                CORRECT_PASSWORD);

        mockMvc.perform(post("/r/" + shortCode + "/unlock")
                .param("password", CORRECT_PASSWORD))
                .andExpect(status().isGone());
    }

    @Test
    void shouldRedirectAfterCorrectPassword() throws Exception {
        String targetUrl = "https://example.com/secret-" + UUID.randomUUID();
        String shortCode = createShortUrl(targetUrl, CORRECT_PASSWORD);

        mockMvc.perform(post("/r/" + shortCode + "/unlock")
                .param("password", CORRECT_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(targetUrl));
    }

    @Test
    void shouldRerenderFormWithErrorOnWrongPassword() throws Exception {
        String shortCode = createShortUrl(
                "https://example.com/" + UUID.randomUUID(),
                CORRECT_PASSWORD);

        mockMvc.perform(post("/r/" + shortCode + "/unlock")
                .param("password", "wrong-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("password-form"))
                .andExpect(model().attribute("shortCode", shortCode))
                .andExpect(model().attributeExists("error"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Incorrect password")));
    }

    @Test
    void shouldHandleUnlockOnUnprotectedUrl_gracefully() throws Exception {
        String targetUrl = "https://example.com/unprotected-" + UUID.randomUUID();
        String shortCode = createShortUrl(targetUrl, null);

        mockMvc.perform(post("/r/" + shortCode + "/unlock")
                .param("password", "any-password-at-all"))
                .andExpect(status().is3xxRedirection()) // should still redirect
                .andExpect(redirectedUrl(targetUrl));
    }

    @Test
    void shouldIncrementVisitCountAfterSuccessfulUnlock() throws Exception {
        String shortCode = createShortUrl(
                "https://example.com/" + UUID.randomUUID(),
                CORRECT_PASSWORD);

        Integer countBefore = urlRepository.findByShortCode(shortCode)
                .orElseThrow().getVisitCount();

        mockMvc.perform(post("/r/" + shortCode + "/unlock")
                .param("password", CORRECT_PASSWORD))
                .andExpect(status().is3xxRedirection());

        UrlShortener after = urlRepository.findByShortCode(shortCode).orElseThrow();
        assertThat(after.getVisitCount()).isEqualTo(countBefore + 1);
        assertThat(after.getLastAccessedAt()).isNotNull();
    }

    @Test
    void shouldNotIncrementVisitCountOnWrongPassword() throws Exception {
        String shortCode = createShortUrl(
                "https://example.com/" + UUID.randomUUID(),
                CORRECT_PASSWORD);

        Integer countBefore = urlRepository.findByShortCode(shortCode)
                .orElseThrow().getVisitCount();

        // Submit wrong password
        mockMvc.perform(post("/r/" + shortCode + "/unlock")
                .param("password", "wrong"))
                .andExpect(status().isOk()) // re-renders form
                .andExpect(view().name("password-form"));

        // Try a few more wrong attempts
        mockMvc.perform(post("/r/" + shortCode + "/unlock")
                .param("password", "also-wrong"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/r/" + shortCode + "/unlock")
                .param("password", "still-wrong"))
                .andExpect(status().isOk());

        UrlShortener after = urlRepository.findByShortCode(shortCode).orElseThrow();
        assertThat(after.getVisitCount()).isEqualTo(countBefore);
    }

    @Test
    void shouldNotIncrementVisitCountOnFormDisplay() throws Exception {
        String shortCode = createShortUrl(
                "https://example.com/" + UUID.randomUUID(),
                CORRECT_PASSWORD);

        Integer countBefore = urlRepository.findByShortCode(shortCode)
                .orElseThrow().getVisitCount();

        // Display the form multiple times
        mockMvc.perform(get("/r/" + shortCode)).andExpect(status().isOk());
        mockMvc.perform(get("/r/" + shortCode)).andExpect(status().isOk());
        mockMvc.perform(get("/r/" + shortCode)).andExpect(status().isOk());

        UrlShortener after = urlRepository.findByShortCode(shortCode).orElseThrow();
        assertThat(after.getVisitCount()).isEqualTo(countBefore);
    }

    @Test
    void shouldNotIncludePasswordHashInAnyResponse() throws Exception {
        String shortCode = createShortUrl(
                "https://example.com/" + UUID.randomUUID(),
                CORRECT_PASSWORD);

        UrlShortener row = urlRepository.findByShortCode(shortCode).orElseThrow();
        String storedHash = row.getPasswordHash();
        assertThat(storedHash).isNotNull();
        assertThat(storedHash).startsWith("$2");

        MvcResult formResult = mockMvc.perform(get("/r/" + shortCode))
                .andExpect(status().isOk())
                .andReturn();
        String formBody = formResult.getResponse().getContentAsString();
        assertThat(formBody).doesNotContain(storedHash);
        assertThat(formBody).doesNotContain("passwordHash");
        assertThat(formBody).doesNotContain(CORRECT_PASSWORD); // also no plaintext

        MvcResult wrongResult = mockMvc.perform(post("/r/" + shortCode + "/unlock")
                .param("password", "wrong"))
                .andExpect(status().isOk())
                .andReturn();
        String wrongBody = wrongResult.getResponse().getContentAsString();
        assertThat(wrongBody).doesNotContain(storedHash);
        assertThat(wrongBody).doesNotContain("passwordHash");
        String createBody = """
                {
                    "originalUrl": "https://example.com/regression-check",
                    "password": "%s"
                }
                """.formatted(CORRECT_PASSWORD);

        MvcResult apiResult = mockMvc.perform(post("/api/shorten")
                .header("X-API-Key", testUser.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        String apiBody = apiResult.getResponse().getContentAsString();
        assertThat(apiBody).doesNotContain("passwordHash");
        assertThat(apiBody).doesNotContain(CORRECT_PASSWORD);

        assertThat(wrongBody).doesNotContain(CORRECT_PASSWORD);
    }

    @Test
    void shouldNotEchoSubmittedPasswordOnWrongAttempt() throws Exception {
        String shortCode = createShortUrl(
                "https://example.com/" + UUID.randomUUID(),
                CORRECT_PASSWORD);

        String attemptedPassword = "user-attempted-this-string-7849";

        MvcResult result = mockMvc.perform(post("/r/" + shortCode + "/unlock")
                .param("password", attemptedPassword))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(attemptedPassword);
    }
}