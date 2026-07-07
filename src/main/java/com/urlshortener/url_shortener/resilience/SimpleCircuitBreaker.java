package com.urlshortener.url_shortener.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * A minimal circuit breaker.
 *
 * States:
 *   CLOSED    - calls pass through; consecutive failures are counted.
 *               After `failureThreshold` failures in a row -> trip to OPEN.
 *   OPEN      - calls are rejected immediately (fail fast) without hitting the
 *               protected resource, for `openMillis`. This is the "stop calling
 *               the database" part — it gives the DB room to recover instead of
 *               piling on more doomed requests.
 *   HALF_OPEN - after the cooldown, ONE trial call is allowed through:
 *                 success -> back to CLOSED (recovered)
 *                 failure -> back to OPEN (wait another cooldown)
 *
 * Configured per the assignment: 3 failures to open, 10s before retrying.
 *
 * Thread-safety: state transitions use atomics + a short synchronized guard so
 * concurrent requests can't corrupt the counters. Kept deliberately simple; a
 * production setup would use Resilience4j (sliding windows, metrics, etc.).
 */
public class SimpleCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(SimpleCircuitBreaker.class);

    public enum State { CLOSED, OPEN, HALF_OPEN }

    /** Thrown when the breaker is OPEN and the call is rejected without executing. */
    public static class CircuitOpenException extends RuntimeException {
        public CircuitOpenException(String msg) { super(msg); }
    }

    private final String name;
    private final int failureThreshold;
    private final long openMillis;
    private final Set<Class<? extends Throwable>> ignoredExceptions;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong openedAt = new AtomicLong(0);

    public SimpleCircuitBreaker(String name, int failureThreshold, long openMillis,
                               Set<Class<? extends Throwable>> ignoredExceptions) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.openMillis = openMillis;
        this.ignoredExceptions = ignoredExceptions == null ? Set.of() : ignoredExceptions;
    }

    /** Convenience factory matching the assignment: 3 failures, 10s cooldown. */
    public static SimpleCircuitBreaker of(String name) {
        return new SimpleCircuitBreaker(name, 3, 10_000L, Set.of());
    }

    /**
     * Factory with ignored exceptions — these propagate to the caller but do NOT
     * count as circuit failures. Use for business/expected exceptions (e.g. a
     * unique-constraint violation) where the DB is actually healthy: it responded
     * correctly by rejecting the request, so it must not trip the breaker.
     */
    public static SimpleCircuitBreaker of(String name,
                                          Set<Class<? extends Throwable>> ignoredExceptions) {
        return new SimpleCircuitBreaker(name, 3, 10_000L, ignoredExceptions);
    }

    private boolean isIgnored(Throwable e) {
        return ignoredExceptions.stream().anyMatch(t -> t.isInstance(e));
    }

    public State getState() {
        return state.get();
    }

    /**
     * Run `action` through the breaker.
     * @throws CircuitOpenException if the breaker is OPEN (call not attempted)
     */
    public <T> T execute(Supplier<T> action) {
        if (!allowRequest()) {
            throw new CircuitOpenException("Circuit '" + name + "' is OPEN — call rejected");
        }
        try {
            T result = action.get();
            onSuccess();
            return result;
        } catch (RuntimeException e) {
            if (isIgnored(e)) {
                // DB responded correctly (e.g. rejected a duplicate) — it's
                // healthy. Count as success for breaker purposes, but still
                // propagate so the caller can handle/translate it.
                onSuccess();
                throw e;
            }
            onFailure();
            throw e; // real failure — count it and propagate
        }
    }

    /**
     * Decide whether a call may proceed, transitioning OPEN -> HALF_OPEN once the
     * cooldown has elapsed.
     */
    private synchronized boolean allowRequest() {
        State current = state.get();
        if (current == State.CLOSED || current == State.HALF_OPEN) {
            return true;
        }
        // current == OPEN: has the cooldown passed?
        long elapsed = System.currentTimeMillis() - openedAt.get();
        if (elapsed >= openMillis) {
            // let a single trial call through
            state.set(State.HALF_OPEN);
            log.info("Circuit '{}' OPEN -> HALF_OPEN (probing after {}ms)", name, elapsed);
            return true;
        }
        return false; // still cooling down -> reject
    }

    private synchronized void onSuccess() {
        // any success resets the failure count; a HALF_OPEN success closes the breaker
        consecutiveFailures.set(0);
        if (state.get() != State.CLOSED) {
            state.set(State.CLOSED);
            log.info("Circuit '{}' -> CLOSED (recovered)", name);
        }
    }

    private synchronized void onFailure() {
        // a failure while probing immediately re-opens
        if (state.get() == State.HALF_OPEN) {
            trip();
            return;
        }
        int failures = consecutiveFailures.incrementAndGet();
        log.warn("Circuit '{}' failure {}/{}", name, failures, failureThreshold);
        if (failures >= failureThreshold) {
            trip();
        }
    }

    private void trip() {
        state.set(State.OPEN);
        openedAt.set(System.currentTimeMillis());
        consecutiveFailures.set(0);
        log.warn("Circuit '{}' -> OPEN (will retry after {}ms)", name, openMillis);
    }
}