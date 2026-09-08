package com.urlshortener.url_shortener.queue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryQueue {

    private static final Logger log = LoggerFactory.getLogger(RetryQueue.class);

    private final BlockingQueue<RetryTask> queue = new LinkedBlockingQueue<>();

    public void enqueue(RetryTask task) {
        queue.add(task);
        log.info("[retry] ENQUEUED task from {} for user id={} (depth now {})",
                task.sourceQueueName(), task.userId(), queue.size());
    }

    public RetryTask take() throws InterruptedException {
        return queue.take();
    }

    public int depth() {
        return queue.size();
    }
}
