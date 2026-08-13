package com.urlshortener.url_shortener.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import com.urlshortener.url_shortener.entity.User;
import com.urlshortener.url_shortener.exception.UserNotFoundException;
import com.urlshortener.url_shortener.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ThumbnailStatusService {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailStatusService.class);

    private final UserRepository userRepository;

    private final Map<Integer, List<DeferredResult<ResponseEntity<StatusResponse>>>> waiters =
            new ConcurrentHashMap<>();

    public record StatusResponse(String status, String url) {}

    public ThumbnailStatusService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public StatusResponse getStatus(Integer userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        if (user == null || user.getImageThumbnail() == null) {
            return new StatusResponse("Pending", null);
        }
        return new StatusResponse("Done", user.getImageThumbnail());
    }

    public void awaitCompletion(Integer userId, DeferredResult<ResponseEntity<StatusResponse>> deferred) {
        waiters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(deferred);
        deferred.onCompletion(() -> {
            List<DeferredResult<ResponseEntity<StatusResponse>>> list = waiters.get(userId);
            if (list != null) list.remove(deferred);
        });
    }

    public void notifyReady(Integer userId, String thumbnailUrl) {
        List<DeferredResult<ResponseEntity<StatusResponse>>> list = waiters.remove(userId);
        if (list == null || list.isEmpty()) {
            return; 
        }
        StatusResponse done = new StatusResponse("Done", thumbnailUrl);
        for (DeferredResult<ResponseEntity<StatusResponse>> d : list) {
            d.setResult(ResponseEntity.ok(done));
        }
        log.info("notified {} long-poll waiter(s) for user id={}", list.size(), userId);
    }
}