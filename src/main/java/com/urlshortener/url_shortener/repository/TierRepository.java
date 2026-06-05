package com.urlshortener.url_shortener.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.urlshortener.url_shortener.entity.Tier;
import com.urlshortener.url_shortener.enums.TierType;

public interface TierRepository extends JpaRepository<Tier, Integer> {
    Optional<Tier> findByName(TierType name);
}
