package com.urlshortener.url_shortener.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables @Scheduled cron jobs. Without this, @Scheduled methods never fire.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}