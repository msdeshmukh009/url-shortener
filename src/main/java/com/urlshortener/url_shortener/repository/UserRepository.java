package com.urlshortener.url_shortener.repository;


import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.urlshortener.url_shortener.entity.User;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByApiKey(String apiKey);
}
