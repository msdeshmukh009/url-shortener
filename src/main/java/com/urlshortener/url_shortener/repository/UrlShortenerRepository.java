package com.urlshortener.url_shortener.repository;

import java.util.Optional;



import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import com.urlshortener.url_shortener.entity.UrlShortener;

public interface UrlShortenerRepository extends JpaRepository<UrlShortener, Integer> {
    Optional<UrlShortener> findByShortCode(String shortCode);
    Optional<UrlShortener> findByShortCodeAndIsDeletedFalse(String shortCode);
    Optional<UrlShortener> findByOriginalUrl(String originalUrl);
    long countByOriginalUrl(String originalUrl);

    Page<UrlShortener> findByUserId(Integer userId, Pageable pageable);
    Page<UrlShortener> findByUserIdAndIsDeletedFalse(Integer userId, Pageable pageable);

    @Modifying  
    long deleteByShortCode(String shortCode);
}
