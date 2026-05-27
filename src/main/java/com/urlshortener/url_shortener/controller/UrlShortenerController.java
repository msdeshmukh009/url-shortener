package com.urlshortener.url_shortener.controller;

import java.net.URI;

import org.hibernate.validator.constraints.URL;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.urlshortener.url_shortener.entity.UrlShortener;
import com.urlshortener.url_shortener.service.UrlShortenerService;
import com.urlshortener.url_shortener.service.UrlShortenerService.ShortenResult;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api")
public class UrlShortenerController {
    private final UrlShortenerService service;

    public UrlShortenerController(UrlShortenerService service) {
        this.service = service;
    }

    @PostMapping("/shorten")
    public ResponseEntity<UrlShortener> shorten(@Valid @RequestBody ShortenRequest request) {
        ShortenResult result = service.shorten(request.originalUrl);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.mapping());
    }

    @GetMapping("/redirect")
    public ResponseEntity<UrlShortener> redirect(@RequestParam String shortCode) {
        String originalUrl = service.resolve(shortCode);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }

    @DeleteMapping("/urls/{shortCode}")
    public ResponseEntity<Void> delete(@PathVariable String shortCode){
        service.deleteShortCode(shortCode);
        return ResponseEntity.noContent().build();
    }

    public record ShortenRequest(
        @NotBlank @URL @Size(max = 2048) 
        String originalUrl
    ) {
    }

    public record DeleteRequest(String shortCode){
    }

    public record MessageResponse(String message) {}
}
