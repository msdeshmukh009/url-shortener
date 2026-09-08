package com.urlshortener.url_shortener.queue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.urlshortener.url_shortener.dto.ThumbnailTask;

public class TaskQueue {

    private static final Logger log = LoggerFactory.getLogger(TaskQueue.class);

    private final String name;
    private final BlockingQueue<ThumbnailTask> queue = new LinkedBlockingQueue<>();

    public TaskQueue(String name) {
        this.name = name;
    }

    public void enqueue(ThumbnailTask task) {
        queue.add(task);
        log.info("[{}] ENQUEUED task for user id={} (depth now {})",
                name, task.userId(), queue.size());
    }

    public ThumbnailTask take() throws InterruptedException {
        return queue.take();
    }

    public int depth() {
        return queue.size();
    }

    public String getName() {
        return name;
    }
}