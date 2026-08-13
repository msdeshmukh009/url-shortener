package com.urlshortener.url_shortener.pubsub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import com.urlshortener.url_shortener.service.ThumbnailService;
import com.urlshortener.url_shortener.service.ThumbnailStatusService;

import jakarta.annotation.PostConstruct;

@Component
public class ThumbnailSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailSubscriber.class);
    private static final String CHANNEL = "image_uploaded";

    private final RedisMessageListenerContainer container;
    private final ThumbnailService thumbnailService;
    private final ThumbnailStatusService thumbnailStatusService;

    public ThumbnailSubscriber(RedisMessageListenerContainer container,
                                   ThumbnailService thumbnailService, ThumbnailStatusService thumbnailStatusService) {
        this.container = container;
        this.thumbnailService = thumbnailService;
        this.thumbnailStatusService = thumbnailStatusService;
    }

    @PostConstruct
    public void register() {
        container.addMessageListener(this, new ChannelTopic(CHANNEL));
        log.info("subscribed to Redis channel '{}'", CHANNEL);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody());
        log.info("RECEIVED on '{}': {}", CHANNEL, body);
        try {
            Integer userId = Integer.valueOf(body.trim());
            String url = thumbnailService.generateThumbnail(userId);
            thumbnailStatusService.notifyReady(userId, url);
            logUpload(userId);
            notifyAdmin(userId);
        } catch (Exception e) {
            log.error("failed handling image_uploaded for '{}'", body, e);
        }
    }

    private void logUpload(Integer userId) throws InterruptedException {
        Thread.sleep(1000);
        log.info("log_upload done for user id={}", userId);
    }

    private void notifyAdmin(Integer userId) throws InterruptedException {
        Thread.sleep(2000);
        log.info("notify_admin done for user id={}", userId);
    }
}