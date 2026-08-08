package com.urlshortener.url_shortener.dev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.urlshortener.url_shortener.repository.UserRepository;

import java.io.InputStream;

@Component
@Profile("dev")
public class ThumbnailTestDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailTestDataSeeder.class);

    private final UserRepository userRepository;

    @Value("${app.thumbnail.seed-count:500}")
    private int seedCount;

    @Value("${app.thumbnail.seed-image:assets/avatar.png}")
    private String seedImageResource;

    public ThumbnailTestDataSeeder(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Only seed if there's nothing already pending, so restarts don't pile on.
        long alreadyPending = userRepository.countUsersNeedingThumbnail();
        if (alreadyPending > 0) {
            log.info("thumbnail seeder: {} users already pending, skipping seed", alreadyPending);
            return;
        }

        ClassPathResource imgFile = new ClassPathResource(seedImageResource);
        if (!imgFile.exists()) {
            log.warn("thumbnail seeder: resource '{}' not found, skipping", seedImageResource);
            return;
        }

        try (InputStream in = imgFile.getInputStream()) {
            byte[] png = in.readAllBytes();
            int updated = userRepository.seedImagesForSubset(png, seedCount);
            log.info("thumbnail seeder: gave {} user(s) an image with NULL thumbnail "
                    + "(the cron will now pick these up)", updated);
        }
    }
}