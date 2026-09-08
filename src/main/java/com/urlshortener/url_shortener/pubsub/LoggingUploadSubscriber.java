package com.urlshortener.url_shortener.pubsub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class LoggingUploadSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(LoggingUploadSubscriber.class);
    private static final String CHANNEL = "image_uploaded";

    private final RedisMessageListenerContainer container;

    public LoggingUploadSubscriber(RedisMessageListenerContainer container) {
        this.container = container;
    }

    @PostConstruct
    public void register() {
        container.addMessageListener(this, new ChannelTopic(CHANNEL));
        log.info("LoggingUploadSubscriber subscribed to '{}'", CHANNEL);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody());
        try {
            Integer userId = Integer.valueOf(body.trim());
            log.info("[log_upload] received for user id={}", userId);
            logUpload(userId);
            log.info("[log_upload] done for user id={}", userId);
        } catch (Exception e) {
            log.error("[log_upload] failed for '{}'", body, e);
        }
    }

    private void logUpload(Integer userId) throws InterruptedException {
        Thread.sleep(1000); // simulate analytics logging (1s)
    }
}