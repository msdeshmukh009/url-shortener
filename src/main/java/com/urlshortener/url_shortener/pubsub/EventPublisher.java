package com.urlshortener.url_shortener.pubsub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final StringRedisTemplate redis;

    public EventPublisher(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void publish(String channel, String message) {
        redis.convertAndSend(channel, message);
        log.info("PUBLISHED to channel '{}': {}", channel, message);
    }
}