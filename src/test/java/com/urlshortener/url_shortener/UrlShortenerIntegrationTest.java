package com.urlshortener.url_shortener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.urlshortener.url_shortener.entity.UrlShortener;
import com.urlshortener.url_shortener.entity.User;
import com.urlshortener.url_shortener.repository.UserRepository;
import com.urlshortener.url_shortener.utils.UrlUtils;

import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
public class UrlShortenerIntegrationTest {
        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        UserRepository userRepository;

        private User user;

        @BeforeEach
        void setup() {
                user = userRepository.save(User.builder()
                                .email("a-" + UUID.randomUUID() + "@test.com")
                                .name("User A")
                                .apiKey("apikey-A-" + UUID.randomUUID())
                                .build());

        }

        @Test
        void shouldShortenUrlAndRedirectCorrectly() throws Exception {
                String url = UrlUtils.generateRandomUrl();
                String requestBody = """
                                {
                                    "originalUrl": "%s"
                                }
                                """.formatted(url);

                MvcResult shortenResult = mockMvc.perform(
                                post("/api/shorten")
                                                .header("X-API-Key", user.getApiKey())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(requestBody))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.shortCode").exists())
                                .andExpect(jsonPath("$.originalUrl").value(url))
                                .andReturn();

                String responseJson = shortenResult.getResponse().getContentAsString();
                UrlShortener saved = objectMapper.readValue(responseJson, UrlShortener.class);
                String shortCode = saved.getShortCode();

                assertThat(shortCode).isNotBlank();
                System.out.println("Short code received: " + shortCode);

                mockMvc.perform(
                                get("/api/redirect")
                                                .param("shortCode", shortCode))
                                .andExpect(status().isFound()) // expect 302 redirect
                                .andExpect(header().string("Location", url));
        }

}
