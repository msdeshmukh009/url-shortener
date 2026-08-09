package com.urlshortener.url_shortener.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class TaskQueue {

    private static final Logger log = LoggerFactory.getLogger(TaskQueue.class);

    /** A unit of work. Here: which user needs a thumbnail. */
    public record ThumbnailTask(Integer userId, long enqueuedAtMs) {}

    private final BlockingQueue<ThumbnailTask> queue = new LinkedBlockingQueue<>();

    /** Producer side — called by the /enqueue endpoint. Returns immediately. */
    public void enqueue(ThumbnailTask task) {
        queue.add(task);
        log.info("ENQUEUED task for user id={} (queue depth now {})",
                task.userId(), queue.size());
    }

    /** Consumer side — called by the worker loop. BLOCKS until a task exists. */
    public ThumbnailTask take() throws InterruptedException {
        return queue.take();
    }

    public int depth() {
        return queue.size();
    }
}