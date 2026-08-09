package com.urlshortener.url_shortener.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.urlshortener.url_shortener.repository.UrlShortenerRepository;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class VisitCountFlusher {

    private static final Logger log = LoggerFactory.getLogger(VisitCountFlusher.class);

    private final UrlShortenerRepository repository;

    public VisitCountFlusher(UrlShortenerRepository repository) {
        this.repository = repository;
    }

    /** One transaction for the whole batch — all increments land together. */
    @Transactional
    public void flushAll(Map<Integer, Long> pending, LocalDateTime now) {
        long total = pending.values().stream().mapToLong(v -> v).sum();
        log.info("FLUSH: {} url(s), {} total visits", pending.size(), total);
        pending.forEach((urlId, count) ->
                repository.incrementVisitCountBy(urlId, count, now));
    }
}