package com.urlshortener.url_shortener.repository;


import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.urlshortener.url_shortener.entity.User;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByApiKey(String apiKey);

    @Query("SELECT u FROM User u JOIN FETCH u.tier WHERE u.apiKey = :apiKey")
    Optional<User> findByApiKeyWithTier(@Param("apiKey") String apiKey);
}
