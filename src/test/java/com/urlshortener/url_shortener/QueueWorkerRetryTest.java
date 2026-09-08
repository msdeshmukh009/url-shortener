package com.urlshortener.url_shortener;

import org.junit.jupiter.api.Test;

import com.urlshortener.url_shortener.dto.ThumbnailTask;
import com.urlshortener.url_shortener.queue.QueueWorker;
import com.urlshortener.url_shortener.queue.RetryQueue;
import com.urlshortener.url_shortener.queue.RetryQueueWorker;
import com.urlshortener.url_shortener.queue.TaskQueue;
import com.urlshortener.url_shortener.service.ThumbnailService;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class QueueWorkerRetryTest {

    private static final int DEFAULT_MAX_ATTEMPTS = 5;

    @Test
    void failedTask_movesToRetryQueue_andLeavesMainQueueEmpty() throws Exception {
        TaskQueue thumbnailQueue = new TaskQueue("generate_thumbnail");
        TaskQueue logUploadQueue = new TaskQueue("log_upload");
        TaskQueue notifyAdminQueue = new TaskQueue("notify_admin");
        RetryQueue retryQueue = new RetryQueue();

        Integer userId = 42;
        ThumbnailService thumbnailService = mock(ThumbnailService.class);
        doThrow(new RuntimeException("simulated failure processing user " + userId))
                .when(thumbnailService).generateThumbnail(eq(userId));

        QueueWorker worker = new QueueWorker(
                thumbnailQueue, logUploadQueue, notifyAdminQueue, retryQueue, thumbnailService);
        worker.start();
        try {
            thumbnailQueue.enqueue(new ThumbnailTask(userId, System.currentTimeMillis()));

            Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
            while (Instant.now().isBefore(deadline) && retryQueue.depth() < 1) {
                Thread.sleep(50);
            }

            assertThat(retryQueue.depth())
                    .as("failed task should be moved to the retry queue, not left on/put back on the main queue")
                    .isEqualTo(1);
            assertThat(thumbnailQueue.depth())
                    .as("main queue should stay clean of failing messages once they've moved to retry")
                    .isEqualTo(0);
        } finally {
            worker.stop();
        }
    }

    @Test
    void taskThatFailsUnderCap_isRetriedByRetryWorker_andEventuallySucceeds() throws Exception {
        TaskQueue thumbnailQueue = new TaskQueue("generate_thumbnail");
        TaskQueue logUploadQueue = new TaskQueue("log_upload");
        TaskQueue notifyAdminQueue = new TaskQueue("notify_admin");
        RetryQueue retryQueue = new RetryQueue();

        Integer userId = 42;
        ThumbnailService thumbnailService = mock(ThumbnailService.class);
        // fails on the main-queue attempt + first 2 retries, succeeds on the 3rd retry (well under the cap of 5)
        doThrow(new RuntimeException("simulated failure processing user " + userId))
                .doThrow(new RuntimeException("simulated failure processing user " + userId))
                .doThrow(new RuntimeException("simulated failure processing user " + userId))
                .doReturn("thumb_42.png")
                .when(thumbnailService).generateThumbnail(eq(userId));

        QueueWorker worker = new QueueWorker(
                thumbnailQueue, logUploadQueue, notifyAdminQueue, retryQueue, thumbnailService);
        RetryQueueWorker retryWorker = new RetryQueueWorker(retryQueue, DEFAULT_MAX_ATTEMPTS);
        worker.start();
        retryWorker.start();
        try {
            thumbnailQueue.enqueue(new ThumbnailTask(userId, System.currentTimeMillis()));

            Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
            while (Instant.now().isBefore(deadline)
                    && mockingDetails(thumbnailService).getInvocations().size() < 4) {
                Thread.sleep(50);
            }

            // 1 attempt on the main queue worker + 3 attempts on the retry worker = 4, then success
            verify(thumbnailService, times(4)).generateThumbnail(eq(userId));
        } finally {
            worker.stop();
            retryWorker.stop();
        }
    }

    @Test
    void taskThatNeverSucceeds_isRetriedUpToMaxAttempts_thenGivenUp() throws Exception {
        TaskQueue thumbnailQueue = new TaskQueue("generate_thumbnail");
        TaskQueue logUploadQueue = new TaskQueue("log_upload");
        TaskQueue notifyAdminQueue = new TaskQueue("notify_admin");
        RetryQueue retryQueue = new RetryQueue();

        Integer userId = 7;
        int maxAttempts = 3;
        ThumbnailService thumbnailService = mock(ThumbnailService.class);
        doThrow(new RuntimeException("always fails for user " + userId))
                .when(thumbnailService).generateThumbnail(eq(userId));

        QueueWorker worker = new QueueWorker(
                thumbnailQueue, logUploadQueue, notifyAdminQueue, retryQueue, thumbnailService);
        RetryQueueWorker retryWorker = new RetryQueueWorker(retryQueue, maxAttempts);
        worker.start();
        retryWorker.start();
        try {
            thumbnailQueue.enqueue(new ThumbnailTask(userId, System.currentTimeMillis()));

            // 1 attempt on the main queue + maxAttempts attempts on the retry worker, then it must stop
            int expectedInvocations = maxAttempts + 1;
            Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
            while (Instant.now().isBefore(deadline)
                    && mockingDetails(thumbnailService).getInvocations().size() < expectedInvocations) {
                Thread.sleep(50);
            }
            // give the worker a moment to prove it has actually stopped, not just paused between retries
            Thread.sleep(300);

            verify(thumbnailService, times(expectedInvocations)).generateThumbnail(eq(userId));
            assertThat(retryQueue.depth())
                    .as("worker should give up after maxAttempts instead of retrying forever")
                    .isEqualTo(0);
            assertThat(thumbnailQueue.depth())
                    .as("a permanently-failing task must never land back on the main queue")
                    .isEqualTo(0);
        } finally {
            worker.stop();
            retryWorker.stop();
        }
    }
}
