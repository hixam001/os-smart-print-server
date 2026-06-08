package com.printscheduler.model;

import java.util.concurrent.atomic.AtomicInteger;

public class CountingSemaphore {

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

    public synchronized void acquire() throws InterruptedException {
        String thread = Thread.currentThread().getName();

        if (permits <= 0) {

            waiters.incrementAndGet();
            fireEvent("SEMAPHORE_WAIT", thread);
            while (permits <= 0) {
                wait();
            }
            waiters.decrementAndGet();
        }

        permits--;
        fireEvent("SEMAPHORE_ACQUIRED", thread);
    }

    public synchronized void release() {
        String thread = Thread.currentThread().getName();
        permits++;
        notify();
        fireEvent("SEMAPHORE_RELEASED", thread);
    }

    public synchronized int getPermits() { return permits; }

    public int getWaiters() { return waiters.get(); }

    private void fireEvent(String type, String thread) {
        SemaphoreEventListener l = this.eventListener;
        if (l != null) {
            try {
                l.onEvent(type, thread, permits, waiters.get());
            } catch (Exception ignored) {}
        }
    }
}
