package com.urlshortener.url_shortener.controller;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.urlshortener.url_shortener.entity.UrlShortener;
import com.urlshortener.url_shortener.service.UrlShortenerService;

@RestController
@RequestMapping("/api")
public class UrlShortenerController {
    private final UrlShortenerService service;

    public UrlShortenerController(UrlShortenerService service) {
        this.service = service;
    }

    @PostMapping("/shorten")
    public ResponseEntity<UrlShortener> shorten(@RequestBody ShortenRequest request) {
        UrlShortener saved = service.shorten(request.originalUrl);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/redirect")
    public ResponseEntity<UrlShortener> redirect(@RequestParam String shortCode) {
        String originalUrl = service.resolve(shortCode);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }

    public record ShortenRequest(String originalUrl) {
    }
}
