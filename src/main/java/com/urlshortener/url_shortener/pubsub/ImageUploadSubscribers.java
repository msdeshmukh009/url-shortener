package com.urlshortener.url_shortener.pubsub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.urlshortener.url_shortener.queue.QueueWorker;
import com.urlshortener.url_shortener.service.ThumbnailService;

@Component
public class ImageUploadSubscribers {
    private static final Logger log = LoggerFactory.getLogger(QueueWorker.class);

    public ImageUploadSubscribers(EventBus bus, ThumbnailService thumbnailService) {
        bus.subscribe(new EventBus.Subscriber() {
            public String eventName() {
                return Events.IMAGE_UPLOADED;
            }

            public void handle(Object data) {
                thumbnailService.generateThumbnail((Integer) data);
            }
        });

        bus.subscribe(new EventBus.Subscriber() {
            public String eventName() {
                return Events.IMAGE_UPLOADED;
            }

            public void handle(Object data) {
                try {
                    Thread.sleep(1000);
                    if (data instanceof Integer) {
                        log.info("log_upload done for user id={}", data);
                    }
                } catch (InterruptedException e) {
                    log.error("log_upload failed for user id={}", data);
                }
            }
        });

        bus.subscribe(new EventBus.Subscriber() {
            public String eventName() {
                return Events.IMAGE_UPLOADED;
            }

            public void handle(Object data) {
                try {
                    Thread.sleep(1000);
                    if (data instanceof Integer) {
                        log.info("notified_admin for user id={}", data);
                    }
                } catch (InterruptedException e) {
                    log.error("failed to notify admin for user id={}", data);
                }
            }
        });
    }
}
