package com.urlshortener.url_shortener;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.urlshortener.url_shortener.entity.UrlShortener;

import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
public class UrlShortenerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldShortenUrlAndRedirectCorrectly() throws Exception {
        String requestBody = """
                {
                    "originalUrl": "https://example.com"
                }
                """;

        MvcResult shortenResult = mockMvc.perform(
                post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated()) // expect 201
                .andExpect(jsonPath("$.shortCode").exists()) // shortCode must be in response
                .andExpect(jsonPath("$.originalUrl").value("https://example.com"))
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
                .andExpect(header().string("Location", "https://example.com"));
    }

}
