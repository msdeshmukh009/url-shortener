package com.urlshortener.url_shortener.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.urlshortener.url_shortener.queue.TaskQueue.ThumbnailTask;
import com.urlshortener.url_shortener.service.ThumbnailService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;


@Component
public class QueueWorker {

    private static final Logger log = LoggerFactory.getLogger(QueueWorker.class);

    private final TaskQueue taskQueue;
    private final ThumbnailService thumbnailService;

    private Thread worker;
    private volatile boolean running = true;

    public QueueWorker(TaskQueue taskQueue, ThumbnailService thumbnailService) {
        this.taskQueue = taskQueue;
        this.thumbnailService = thumbnailService;
    }

    @PostConstruct
    public void start() {
        worker = new Thread(this::loop, "queue-worker");
        worker.setDaemon(true);
        worker.start();
        log.info("queue worker started on thread {}", worker.getName());
    }

    private void loop() {
        while (running) {
            try {
                // BLOCKS here until a task is available — no busy polling
                ThumbnailTask task = taskQueue.take();
                long waitedMs = System.currentTimeMillis() - task.enqueuedAtMs();
                log.info("WORKER picked up task for user id={} (waited {}ms in queue) on thread {}",
                        task.userId(), waitedMs, Thread.currentThread().getName());

                thumbnailService.generateThumbnail(task.userId());

                log.info("WORKER finished task for user id={}", task.userId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("queue worker interrupted, shutting down");
                break;
            } catch (Exception e) {
                // one bad task must not kill the loop
                log.error("WORKER error processing task", e);
            }
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
    }
}