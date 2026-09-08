package com.urlshortener.url_shortener.queue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class RetryQueueWorker {

    private static final Logger log = LoggerFactory.getLogger(RetryQueueWorker.class);

    private final RetryQueue retryQueue;
    private final int maxAttempts;

    private ExecutorService executor;
    private volatile boolean running = true;

    public RetryQueueWorker(RetryQueue retryQueue,
            @Value("${app.retry.max-attempts:5}") int maxAttempts) {
        this.retryQueue = retryQueue;
        this.maxAttempts = maxAttempts;
    }

    @PostConstruct
    public void start() {
        executor = Executors.newSingleThreadExecutor();
        executor.submit(this::consume);
        log.info("started retry queue worker");
    }

    private void consume() {
        while (running) {
            try {
                RetryTask task = retryQueue.take();
                long waited = System.currentTimeMillis() - task.enqueuedAtMs();
                log.info("[retry] worker on {} picked up user id={} (originally from {}, waited {}ms, depth now {})",
                        Thread.currentThread().getName(), task.userId(), task.sourceQueueName(), waited, retryQueue.depth());
                try {
                    task.handler().handle(task.userId());
                    log.info("[retry] succeeded for user id={} (originally from {})",
                            task.userId(), task.sourceQueueName());
                } catch (Exception e) {
                    if (task.attempt() >= maxAttempts) {
                        log.error("[retry] giving up on user id={} (originally from {}) after {} attempts, dead-lettering",
                                task.userId(), task.sourceQueueName(), task.attempt(), e);
                    } else {
                        log.error("[retry] attempt {} failed for user id={} (originally from {}), re-enqueueing (attempt {})",
                                task.attempt(), task.userId(), task.sourceQueueName(), task.attempt() + 1, e);
                        retryQueue.enqueue(new RetryTask(task.sourceQueueName(), task.userId(), task.handler(),
                                task.enqueuedAtMs(), task.attempt() + 1));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("[retry] worker error", e);
            }
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
