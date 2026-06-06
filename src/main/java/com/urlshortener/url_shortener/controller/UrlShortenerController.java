package com.urlshortener.url_shortener.controller;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.hibernate.validator.constraints.URL;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.urlshortener.url_shortener.dto.BulkShortenResponse;
import com.urlshortener.url_shortener.dto.ShortenResponse;
import com.urlshortener.url_shortener.entity.UrlShortener;
import com.urlshortener.url_shortener.entity.User;
import com.urlshortener.url_shortener.service.UrlShortenerService;
import com.urlshortener.url_shortener.service.UserService;
import com.urlshortener.url_shortener.service.UrlShortenerService.ShortenResult;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api")
@Validated
public class UrlShortenerController {
    private final UrlShortenerService service;
    private final UserService userService;

    public UrlShortenerController(UrlShortenerService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    @PostMapping("/shorten")
    public ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request,
            @RequestHeader("X-API-Key") @NotBlank(message = "API key cannot be empty") String apiKey) {
        User user = userService.findByApiKey(apiKey);
        ShortenResult result = service.shorten(user, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ShortenResponse.from(result.mapping()));
    }

    @GetMapping("/redirect")
    public ResponseEntity<UrlShortener> redirect(@RequestParam String shortCode) {
        String originalUrl = service.resolve(shortCode);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }

    @DeleteMapping("/urls/{shortCode}")
    public ResponseEntity<Void> delete(@PathVariable String shortCode,
            @RequestHeader("X-API-Key") @NotBlank(message = "API key cannot be empty") String apiKey) {
        User user = userService.findByApiKey(apiKey);
        service.deleteShortCode(shortCode, user.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/shorten/bulk")
    public ResponseEntity<BulkShortenResponse> bulkShorten(@Valid @RequestBody BulkShortenRequest request,
            @RequestHeader("X-API-Key") @NotBlank(message = "API key cannot be empty") String apiKey) {
        User user = userService.findByApiKey(apiKey);
        BulkShortenResponse response = service.bulkShorten(user, request.urls);
        HttpStatus status;
        if (response.failed() == 0) {
            status = HttpStatus.CREATED; // 201 — all succeeded
        } else if (response.succeeded() == 0) {
            status = HttpStatus.BAD_REQUEST; // 400 — all failed
        } else {
            status = HttpStatus.MULTI_STATUS; // 207 — partial success
        }
        return ResponseEntity.status(status).body(response);
    }

    @PatchMapping("/shorten/{shortCode}")
    public ResponseEntity<ShortenResponse> edit(@PathVariable String shortCode,
            @RequestHeader("X-API-Key") @NotBlank(message = "API key cannot be empty") String apiKey,
            @Valid @RequestBody EditRequest request) {
        User user = userService.findByApiKey(apiKey);
        ShortenResult result = service.edit(user, request.expiresAt, shortCode);
        return ResponseEntity.status(HttpStatus.OK).body(ShortenResponse.from(result.mapping()));
    }

    public record ShortenRequest(
            @NotBlank @URL @Size(max = 2048) String originalUrl,
            @Future(message = "Expiry must be in the future") Instant expiresAt,
            @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters") String password,
            @Size(min = 3, max = 50, message = "Short code must be 3-50 characters") @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Short code can only contain letters, numbers, hyphens, and underscores") String shortCode) {
    }

    public record BulkShortenRequest(
            @NotEmpty(message = "The URLs list cannot be empty") @Size(max = 100, message = "Cannot process more than 100 URLs in a single request") List<@Valid ShortenRequest> urls) {
    }

    public record EditRequest(
            Instant expiresAt) {
    }

    public record DeleteRequest(String shortCode) {
    }

    public record MessageResponse(String message) {
    }
}
