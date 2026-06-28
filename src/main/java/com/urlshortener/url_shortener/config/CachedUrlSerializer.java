package com.urlshortener.url_shortener.config;

import com.urlshortener.url_shortener.dto.CachedUrl;

import tools.jackson.databind.ObjectMapper;

import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

public class CachedUrlSerializer implements RedisSerializer<CachedUrl> {

    private final ObjectMapper objectMapper;

    public CachedUrlSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public byte[] serialize(CachedUrl value) throws SerializationException {
        if (value == null) {
            return new byte[0];
        }
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new SerializationException("Could not serialize CachedUrl", e);
        }
    }

    @Override
    public CachedUrl deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return objectMapper.readValue(bytes, CachedUrl.class);
        } catch (Exception e) {
            throw new SerializationException("Could not deserialize CachedUrl", e);
        }
    }
}