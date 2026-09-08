package com.urlshortener.url_shortener.queue;

public record RetryTask(String sourceQueueName, Integer userId, TaskHandler handler, long enqueuedAtMs, int attempt) {}
