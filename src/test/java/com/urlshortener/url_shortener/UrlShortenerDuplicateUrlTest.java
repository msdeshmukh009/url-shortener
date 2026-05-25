package com.urlshortener.url_shortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.UnsupportedEncodingException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.urlshortener.url_shortener.entity.UrlShortener;
import com.urlshortener.url_shortener.repository.UrlShortenerRepository;
import com.urlshortener.url_shortener.utils.UrlUtils;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.MediaType;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
public class UrlShortenerDuplicateUrlTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    
     @Autowired 
     private UrlShortenerRepository repository;

    @Test
    void shouldReturnSameShortCodeForSameOriginalUrl() throws Exception {
        String url = UrlUtils.generateRandomUrl();
        assertThat(repository.countByOriginalUrl(url)).isZero();

        String code1 = generateShortCode(url, 201);
        assertThat(repository.countByOriginalUrl(url)).isEqualTo(1);

        String code2 = generateShortCode(url, 200);
        assertThat(repository.countByOriginalUrl(url)).isEqualTo(1);
        
        assertThat(code1).isEqualTo(code2);
    }

    private String generateShortCode(String url, int expectedStatus) throws Exception, UnsupportedEncodingException {

        String requestBody = """
                {
                    "originalUrl": "%s"
                }
                """.formatted(url);
        MvcResult shortenResult = mockMvc.perform(
                post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.shortCode").exists()) // shortCode must be in response
                .andExpect(jsonPath("$.originalUrl").value(url))
                .andReturn();

        String responseJson = shortenResult.getResponse().getContentAsString();
        UrlShortener saved = objectMapper.readValue(responseJson, UrlShortener.class);
        String shortCode = saved.getShortCode();
        return shortCode;
    }
}
