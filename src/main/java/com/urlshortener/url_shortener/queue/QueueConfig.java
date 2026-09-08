package com.urlshortener.url_shortener.queue;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueueConfig {

    @Bean
    public TaskQueue thumbnailQueue() {
        return new TaskQueue("generate_thumbnail");
    }

    @Bean
    public TaskQueue logUploadQueue() {
        return new TaskQueue("log_upload");
    }

    @Bean
    public TaskQueue notifyAdminQueue() {
        return new TaskQueue("notify_admin");
    }

    @Bean
    public RetryQueue retryQueue() {
        return new RetryQueue();
    }
}