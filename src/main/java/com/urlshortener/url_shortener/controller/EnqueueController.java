package com.urlshortener.url_shortener.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.urlshortener.url_shortener.queue.TaskQueue;
import com.urlshortener.url_shortener.queue.TaskQueue.ThumbnailTask;

import java.time.LocalTime;

@RestController
public class EnqueueController {

    private static final Logger log = LoggerFactory.getLogger(EnqueueController.class);

    private final TaskQueue taskQueue;

    public EnqueueController(TaskQueue taskQueue) {
        this.taskQueue = taskQueue;
    }

    @PostMapping("/enqueue")
    public ResponseEntity<String> enqueue(@RequestParam Integer userId) {
        log.info(">> /enqueue received for user id={} at {}", userId, LocalTime.now());

        taskQueue.enqueue(new ThumbnailTask(userId, System.currentTimeMillis()));

        log.info("<< /enqueue returning 202 for user id={} at {} (thumbnail NOT done yet)",
                userId, LocalTime.now());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body("Accepted — thumbnail for user " + userId + " is being generated");
    }
}