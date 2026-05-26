package com.urlshortener.url_shortener;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.urlshortener.url_shortener.entity.UrlShortener;
import com.urlshortener.url_shortener.repository.UrlShortenerRepository;
import com.urlshortener.url_shortener.utils.UrlUtils;

import tools.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;

@SpringBootTest
@AutoConfigureMockMvc
public class DeleteShortCodeTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UrlShortenerRepository repository;

    private String createShortCode(String url) throws Exception {
        String body = """
                { "originalUrl": "%s" }
                """.formatted(url);

        MvcResult result = mockMvc.perform(post("/api/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        UrlShortener saved = objectMapper.readValue(
                result.getResponse().getContentAsString(), UrlShortener.class);
        return saved.getShortCode();
    }

    @Test
    void shouldDeleteExistingShortCode() throws Exception {
        String url = UrlUtils.generateRandomUrl();
        String shortCode = createShortCode(url);

        assertThat(repository.findByShortCode(shortCode)).isPresent();

        mockMvc.perform(delete("/api/urls/" + shortCode))
                .andExpect(status().isNoContent());

        assertThat(repository.findByShortCode(shortCode)).isEmpty();
    }
}
