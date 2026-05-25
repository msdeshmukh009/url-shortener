package com.urlshortener.url_shortener.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.urlshortener.url_shortener.entity.UrlShortener;

public interface UrlShortenerRepository extends JpaRepository<UrlShortener, Integer> {
    Optional<UrlShortener> findByShortCode(String shortCode);
    Optional<UrlShortener> findByOriginalUrl(String originalUrl);
    long countByOriginalUrl(String originalUrl);
}
