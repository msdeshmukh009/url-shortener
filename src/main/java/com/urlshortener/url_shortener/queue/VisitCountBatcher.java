package com.urlshortener.url_shortener.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.urlshortener.url_shortener.repository.UrlShortenerRepository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Batches URL visit-count increments instead of writing to the DB on every
 * redirect. Multiple visits to the SAME short code collapse into one "+N"
 * update, and the write happens off the redirect hot path.
 *
 * Three flush strategies (app.viewcount.mode):
 * INTERVAL - flush every N ms
 * THRESHOLD - flush when pending count reaches N
 * HYBRID - flush on whichever comes first (production default)
 *
 * Visit counts are non-critical (staleness + rare loss acceptable), which is
 * exactly what makes aggressive batching safe here.
 */
@Component
public class VisitCountBatcher {

    private static final Logger log = LoggerFactory.getLogger(VisitCountBatcher.class);

    public enum Mode {
        INTERVAL, THRESHOLD, HYBRID
    }

    private final VisitCountFlusher flusher;

    @Value("${app.viewcount.mode:HYBRID}")
    private Mode mode;

    @Value("${app.viewcount.flush-interval-ms:300000}") // 5 minutes
    private long flushIntervalMs;

    @Value("${app.viewcount.flush-threshold:100}") // 100 visits
    private int flushThreshold;

    private final BlockingQueue<Integer> incoming = new LinkedBlockingQueue<>();

    private Thread worker;
    private volatile boolean running = true;

    public VisitCountBatcher(UrlShortenerRepository repository, VisitCountFlusher flusher) {
        this.flusher = flusher;
    }

    public void recordVisit(Integer urlId) {
        incoming.add(urlId);
    }

    @PostConstruct
    public void start() {
        worker = new Thread(this::loop, "visitcount-batcher");
        worker.setDaemon(true);
        worker.start();
        log.info("visit-count batcher started in {} mode (interval={}ms, threshold={})",
                mode, flushIntervalMs, flushThreshold);
    }

    private void loop() {
        Map<Integer, Long> pending = new HashMap<>(); 
        long lastFlush = System.currentTimeMillis();

        while (running) {
            try {
                long waitMs = flushIntervalMs - (System.currentTimeMillis() - lastFlush);
                Integer urlId = (mode == Mode.THRESHOLD)
                        ? incoming.take() 
                        : incoming.poll(Math.max(1, waitMs), TimeUnit.MILLISECONDS); 

                if (urlId != null) {
                    pending.merge(urlId, 1L, (Long a, Long b) -> a + b);
                }

                if (shouldFlush(pending, lastFlush)) {
                    if (!pending.isEmpty()) {
                        flush(pending);
                        pending = new HashMap<>();
                    }
                    lastFlush = System.currentTimeMillis();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("visit-count batcher error", e);
            }
        }
        if (!pending.isEmpty()) {
            flush(pending);
        }
    }

    private boolean shouldFlush(Map<Integer, Long> pending, long lastFlush) {
        int pendingCount = pending.values().stream().mapToInt(v -> v.intValue()).sum();
        boolean thresholdHit = pendingCount >= flushThreshold;
        boolean intervalElapsed = System.currentTimeMillis() - lastFlush >= flushIntervalMs;
        return switch (mode) {
            case THRESHOLD -> thresholdHit;
            case INTERVAL -> intervalElapsed;
            case HYBRID -> thresholdHit || intervalElapsed;
        };
    }

    private void flush(Map<Integer, Long> pending) {
        flusher.flushAll(pending, LocalDateTime.now());
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (worker != null)
            worker.interrupt();
    }
}