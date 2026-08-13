package com.urlshortener.url_shortener.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import com.urlshortener.url_shortener.service.ThumbnailStatusService;
import com.urlshortener.url_shortener.service.ThumbnailStatusService.StatusResponse;

@RestController
public class ThumbnailStatusController {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailStatusController.class);
    private static final long TIMEOUT_MS = 30_000;

    private final ThumbnailStatusService statusService;

    public ThumbnailStatusController(ThumbnailStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/thumbnailStatusLong")
    public DeferredResult<ResponseEntity<StatusResponse>> longPoll(@RequestParam Integer userId) {
        DeferredResult<ResponseEntity<StatusResponse>> deferred = new DeferredResult<>(TIMEOUT_MS,
                ResponseEntity.ok(new StatusResponse("Pending", null)));

        StatusResponse current = statusService.getStatus(userId);
        if ("Done".equals(current.status())) {
            deferred.setResult(ResponseEntity.ok(current));
            return deferred;
        }

        statusService.awaitCompletion(userId, deferred);

        deferred.onTimeout(() -> log.debug("long-poll timed out for user id={}, client will re-poll", userId));
        return deferred;
    }
}