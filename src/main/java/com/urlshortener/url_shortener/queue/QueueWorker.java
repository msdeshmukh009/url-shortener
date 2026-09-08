package com.urlshortener.url_shortener.queue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.urlshortener.url_shortener.dto.ThumbnailTask;
import com.urlshortener.url_shortener.service.ThumbnailService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class QueueWorker {

    private static final Logger log = LoggerFactory.getLogger(QueueWorker.class);

    private final TaskQueue thumbnailQueue;
    private final TaskQueue logUploadQueue;
    private final TaskQueue notifyAdminQueue;
    private final RetryQueue retryQueue;
    private final ThumbnailService thumbnailService;

    private ExecutorService executor;
    private volatile boolean running = true;

    public QueueWorker(
            @Qualifier("thumbnailQueue") TaskQueue thumbnailQueue,
            @Qualifier("logUploadQueue") TaskQueue logUploadQueue,
            @Qualifier("notifyAdminQueue") TaskQueue notifyAdminQueue,
            RetryQueue retryQueue,
            ThumbnailService thumbnailService) {
        this.thumbnailQueue = thumbnailQueue;
        this.logUploadQueue = logUploadQueue;
        this.notifyAdminQueue = notifyAdminQueue;
        this.retryQueue = retryQueue;
        this.thumbnailService = thumbnailService;
    }

    @PostConstruct
    public void start() {
        // one dedicated worker thread per queue
        executor = Executors.newFixedThreadPool(3);
        executor.submit(() -> consume(thumbnailQueue, this::generateThumbnail));
        executor.submit(() -> consume(logUploadQueue, this::logUpload));
        executor.submit(() -> consume(notifyAdminQueue, this::notifyAdmin));
        log.info("started 3 queue workers (thumbnail, log_upload, notify_admin)");
    }

    private void consume(TaskQueue queue, TaskHandler handler) {
        while (running) {
            try {
                ThumbnailTask task = queue.take();
                long waited = System.currentTimeMillis() - task.enqueuedAtMs();
                log.info("[{}] worker on {} picked up user id={} (waited {}ms)",
                        queue.getName(), Thread.currentThread().getName(), task.userId(), waited);
                try {
                    handler.handle(task.userId());
                } catch (Exception e) {
                    log.error("[{}] failed to process user id={}, moving to retry queue",
                            queue.getName(), task.userId(), e);
                    retryQueue.enqueue(new RetryTask(queue.getName(), task.userId(), handler, task.enqueuedAtMs(), 1));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("[{}] worker error", queue.getName(), e);
            }
        }
    }

    // ---- the three task functions -------------------------------------

    private void generateThumbnail(Integer userId) {
        thumbnailService.generateThumbnail(userId);
        log.info("generate_thumbnail done for user id={}", userId);
    }

    private void logUpload(Integer userId) throws InterruptedException {
        Thread.sleep(1000);
        log.info("log_upload done for user id={}", userId);
    }

    private void notifyAdmin(Integer userId) throws InterruptedException {
        Thread.sleep(2000);
        log.info("notify_admin done for user id={}", userId);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}