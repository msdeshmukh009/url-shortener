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
public class NotifyAdminSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(NotifyAdminSubscriber.class);
    private static final String CHANNEL = "image_uploaded";

    private final RedisMessageListenerContainer container;

    public NotifyAdminSubscriber(RedisMessageListenerContainer container) {
        this.container = container;
    }

    @PostConstruct
    public void register() {
        container.addMessageListener(this, new ChannelTopic(CHANNEL));
        log.info("NotifyAdminSubscriber subscribed to '{}'", CHANNEL);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody());
        try {
            Integer userId = Integer.valueOf(body.trim());
            log.info("[notify_admin] received for user id={}", userId);
            notifyAdmin(userId);
            log.info("[notify_admin] done for user id={}", userId);
        } catch (Exception e) {
            log.error("[notify_admin] failed for '{}'", body, e);
        }
    }

    private void notifyAdmin(Integer userId) throws InterruptedException {
        Thread.sleep(2000); // simulate slack message (2s)
    }
}