package com.urlshortener.url_shortener.queue;

@FunctionalInterface
public interface TaskHandler {
    void handle(Integer userId) throws Exception;
}
