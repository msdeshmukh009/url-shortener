package com.urlshortener.url_shortener.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.urlshortener.url_shortener.dto.ThumbnailTask;
import com.urlshortener.url_shortener.queue.TaskQueue;

import java.time.LocalTime;

@RestController
public class EnqueueController {

    private static final Logger log = LoggerFactory.getLogger(EnqueueController.class);

    private final TaskQueue thumbnailQueue;
    private final TaskQueue logUploadQueue;
    private final TaskQueue notifyAdminQueue;

    public EnqueueController(
            @Qualifier("thumbnailQueue") TaskQueue thumbnailQueue,
            @Qualifier("logUploadQueue") TaskQueue logUploadQueue,
            @Qualifier("notifyAdminQueue") TaskQueue notifyAdminQueue) {
        this.thumbnailQueue = thumbnailQueue;
        this.logUploadQueue = logUploadQueue;
        this.notifyAdminQueue = notifyAdminQueue;
    }

    @PostMapping("/enqueue")
    public ResponseEntity<String> enqueue(@RequestParam Integer userId) {
        log.info(">> /enqueue received for user id={} at {}", userId, LocalTime.now());
        long now = System.currentTimeMillis();
        thumbnailQueue.enqueue(new ThumbnailTask(userId, now));
        logUploadQueue.enqueue(new ThumbnailTask(userId, now));
        notifyAdminQueue.enqueue(new ThumbnailTask(userId, now));

        log.info("<< /enqueue returning 202 for user id={} at {} (thumbnail NOT done yet)",
                userId, LocalTime.now());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body("Accepted — thumbnail for user " + userId + " is being generated");
    }
}