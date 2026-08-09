package com.urlshortener.url_shortener.service;

import java.time.LocalTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class SlowTaskService {
    private static final Logger log = LoggerFactory.getLogger(SlowTaskService.class);

    public String slowTask() {
        runFor3Seconds("SYNC");
        return "Done";
    }

    @Async("taskExecutor")
    public void slowTaskAsync() {
        runFor3Seconds("ASYNC");
    }

    private void runFor3Seconds(String label) {
        log.info("[{}] task STARTED at {} on thread {}",
                label, LocalTime.now(), Thread.currentThread().getName());
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[{}] task interrupted", label);
            return;
        }
        log.info("[{}] task FINISHED at {} on thread {}",
                label, LocalTime.now(), Thread.currentThread().getName());
    }
}
