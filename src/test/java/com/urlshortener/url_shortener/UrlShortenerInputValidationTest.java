package com.urlshortener.url_shortener;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.MediaType;

@SpringBootTest
@AutoConfigureMockMvc
public class UrlShortenerInputValidationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReject400WhenOriginalUrlIsMissing() throws Exception {
        mockMvc.perform(post("/api/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReject400WhenOriginalUrlIsEmpty() throws Exception {
        mockMvc.perform(post("/api/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "originalUrl": "" }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReject400WhenOriginalUrlIsMalformed() throws Exception {
        mockMvc.perform(post("/api/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "originalUrl": "not-a-valid-url" }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReject400WhenOriginalUrlIsTooLong() throws Exception {
        String longUrl = "https://example.com/" + "a".repeat(3000);
        mockMvc.perform(post("/api/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "originalUrl": "%s" }
                        """.formatted(longUrl)))
                .andExpect(status().isBadRequest());
    }
}
