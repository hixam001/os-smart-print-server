package com.printscheduler.model;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Custom Counting Semaphore with waiter tracking and event callbacks.
 *
 * <p>Demonstrates the OS Producer-Consumer synchronization primitive:
 * <ul>
 *   <li>{@code release()} — producer signals "one more item available"</li>
 *   <li>{@code acquire()} — consumer blocks if no items, then takes one</li>
 * </ul>
 *
 * <p>The optional {@link SemaphoreEventListener} lets the simulator
 * broadcast SEMAPHORE_WAIT / SEMAPHORE_ACQUIRED / SEMAPHORE_RELEASED events
 * to the WebSocket layer for live UI visualization.
 */
public class CountingSemaphore {

    /** Called on key semaphore transitions for live event broadcasting. */
    public interface SemaphoreEventListener {
        void onEvent(String eventType, String threadName, int permits, int waiters);
    }

    private int permits;
    private final AtomicInteger waiters = new AtomicInteger(0);
    private volatile SemaphoreEventListener eventListener;

    public CountingSemaphore(int initialPermits) {
        if (initialPermits < 0)
            throw new IllegalArgumentException("Semaphore permits cannot be negative");
        this.permits = initialPermits;
    }

    public void setEventListener(SemaphoreEventListener listener) {
        this.eventListener = listener;
    }

    // ── acquire ───────────────────────────────────────────────────────────

    /**
     * Acquires a permit, blocking if none are available.
     * Fires SEMAPHORE_WAIT → SEMAPHORE_ACQUIRED events for visualization.
     */
    public synchronized void acquire() throws InterruptedException {
        String thread = Thread.currentThread().getName();

        if (permits <= 0) {
            // About to block — notify UI
            waiters.incrementAndGet();
            fireEvent("SEMAPHORE_WAIT", thread);
            while (permits <= 0) {
                wait();   // release monitor, block until notify()
            }
            waiters.decrementAndGet();
        }

        permits--;
        fireEvent("SEMAPHORE_ACQUIRED", thread);
    }

    // ── release ───────────────────────────────────────────────────────────

    /**
     * Releases a permit, waking exactly one waiting thread.
     * Fires SEMAPHORE_RELEASED event.
     */
    public synchronized void release() {
        String thread = Thread.currentThread().getName();
        permits++;
        notify();          // wake one waiting PrinterThread
        fireEvent("SEMAPHORE_RELEASED", thread);
    }

    // ── queries ───────────────────────────────────────────────────────────

    /** Current available permits (jobs in queue). */
    public synchronized int getPermits() { return permits; }

    /** Number of printer threads currently blocked on acquire(). */
    public int getWaiters() { return waiters.get(); }

    // ── private ───────────────────────────────────────────────────────────

    private void fireEvent(String type, String thread) {
        SemaphoreEventListener l = this.eventListener;
        if (l != null) {
            try {
                l.onEvent(type, thread, permits, waiters.get());
            } catch (Exception ignored) {}
        }
    }
}
