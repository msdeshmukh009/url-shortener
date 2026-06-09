package com.urlshortener.url_shortener.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;

@Service
public class BlacklistService {

    private static final Logger log = LoggerFactory.getLogger(BlacklistService.class);

    @Value("classpath:blacklist.yml")
    private Resource blacklistResource;

    private Set<String> blockedKeys = Set.of();

    @PostConstruct
    public void loadBlacklist() {
        if (!blacklistResource.exists()) {
            log.warn("Blacklist file not found. Using empty blacklist.");
            return;
        }

        try (InputStream in = blacklistResource.getInputStream()) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(in);

            if (data == null || !data.containsKey("blocked_keys")) {
                log.info("Blacklist file is empty. No keys to block.");
                return;
            }

            @SuppressWarnings("unchecked")
            List<String> keys = (List<String>) data.get("blocked_keys");

            if (keys != null && !keys.isEmpty()) {
                blockedKeys = Set.copyOf(keys);
            }

            log.info("Loaded {} blocked API keys", blockedKeys.size());

        } catch (IOException e) {
            log.error("Failed to load blacklist. Using empty blacklist.", e);
        }
    }

    public boolean isBlocked(String apiKey) {
        if (apiKey == null) {
            return false;
        }
        return blockedKeys.contains(apiKey);
    }
}