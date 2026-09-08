package com.urlshortener.url_shortener.pubsub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A single pub/sub event bus. Publishers announce events by NAME; subscribers
 * register interest in event names and receive matching events.
 */
@Component
public class EventBus {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    public interface Subscriber {
        String eventName();

        void handle(Object data);
    }

    private final BlockingQueue<Event> channel = new LinkedBlockingQueue<>();

    private final Map<String, List<Subscriber>> subscribers = new ConcurrentHashMap<>();

    private record Event(String name, Object data) {
    }

    private ExecutorService dispatcher;
    private volatile boolean running = true;

    public void subscribe(Subscriber subscriber) {
        subscribers.computeIfAbsent(subscriber.eventName(), k -> new CopyOnWriteArrayList<>())
                .add(subscriber);
        log.info("subscribed {} to event '{}'", subscriber.getClass().getSimpleName(), subscriber.eventName());
    }

    public void publish(String eventName, Object data) {
        channel.add(new Event(eventName, data));
    }

    @PostConstruct
    public void start() {
        dispatcher = Executors.newSingleThreadExecutor();
        dispatcher.submit(this::dispatchLoop);
        log.info("event bus started");
    }

    private void dispatchLoop() {
        while (running) {
            try {
                Event event = channel.take();
                List<Subscriber> interested = subscribers.getOrDefault(event.name(), List.of());
                if (interested.isEmpty()) {
                    log.debug("no subscribers for event '{}'", event.name());
                    continue;
                }
                for (Subscriber s : interested) {
                    try {
                        s.handle(event.data());
                    } catch (Exception e) {
                        log.error("subscriber {} failed on event '{}'",
                                s.getClass().getSimpleName(), event.name(), e);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (dispatcher != null)
            dispatcher.shutdownNow();
    }
}