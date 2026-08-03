package com.urlshortener.url_shortener.controller;

import java.time.LocalTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.urlshortener.url_shortener.service.SlowTaskService;

@RestController
public class SlowTaskController {
    private static final Logger log = LoggerFactory.getLogger(SlowTaskController.class);

    private final SlowTaskService slowTaskService;

    public SlowTaskController(SlowTaskService slowTaskService) {
        this.slowTaskService = slowTaskService;
    }

    @GetMapping("/sync")
    public ResponseEntity<String> sync() {
        log.info(">> /sync received at {}", LocalTime.now());
        String result = slowTaskService.slowTask(); // blocks 3s
        log.info("<< /sync responding at {}", LocalTime.now());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/async")
    public ResponseEntity<String> async() {
        log.info(">> /async received at {}", LocalTime.now());
        slowTaskService.slowTaskAsync(); // returns instantly
        log.info("<< /async responding at {} (task still running)", LocalTime.now());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body("Accepted");
    }
}
