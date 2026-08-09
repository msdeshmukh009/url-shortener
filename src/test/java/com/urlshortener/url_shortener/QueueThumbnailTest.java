package com.urlshortener.url_shortener;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.urlshortener.url_shortener.entity.Tier;
import com.urlshortener.url_shortener.entity.User;
import com.urlshortener.url_shortener.enums.TierType;
import com.urlshortener.url_shortener.repository.TierRepository;
import com.urlshortener.url_shortener.repository.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QueueThumbnailTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    TierRepository tierRepository;

    @Test
    void enqueue_returnsImmediately_andWorkerGeneratesThumbnail() throws Exception {
        Tier hobbyTier = tierRepository.findByName(TierType.HOBBY)
                .orElseGet(() -> tierRepository.save(
                        Tier.builder()
                                .name(TierType.HOBBY)
                                .canUseBulkCreation(false)
                                .build()));
        // 1) create a user with an image and NO thumbnail yet
        byte[] pngBytes = tinyPng();
        User user = userRepository.save(User.builder()
                .email("queue-" + UUID.randomUUID() + "@test.com")
                .name("Queue User")
                .tier(hobbyTier)
                .apiKey("apikey-queue-" + UUID.randomUUID())
                .imageFile(pngBytes)
                .imageThumbnail(null)
                .build());

        assertThat(user.getImageThumbnail()).isNull();

        mockMvc.perform(post("/enqueue").param("userId", user.getId().toString()))
                .andExpect(status().isAccepted());

        String thumbnail = pollForThumbnail(user.getId(), Duration.ofSeconds(10));

        assertThat(thumbnail)
                .as("worker should have generated a thumbnail within the timeout")
                .isNotNull();
    }

    /**
     * Poll the DB every 200ms until the user's thumbnail is non-null or we hit
     * the deadline. Returns the thumbnail (or null if it never appeared).
     */
    private String pollForThumbnail(Integer userId, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            User u = userRepository.findById(userId).orElse(null);
            if (u != null && u.getImageThumbnail() != null) {
                return u.getImageThumbnail();
            }
            Thread.sleep(200);
        }
        return null; 
    }

    /** A minimal valid 1x1 PNG so ImageIO.read() succeeds in the worker. */
    private byte[] tinyPng() {
        // 1x1 transparent PNG
        return java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");
    }
}