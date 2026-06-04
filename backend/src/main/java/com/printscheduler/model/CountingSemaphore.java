package com.printscheduler.model;

/**
 * A custom Counting Semaphore implementation for thread synchronization.
 * Uses Java's built-in monitor synchronization (synchronized, wait, notify).
 */
public class CountingSemaphore {
    private int permits;

    public CountingSemaphore(int initialPermits) {
        if (initialPermits < 0) {
            throw new IllegalArgumentException("Semaphore permits cannot be negative");
        }
        this.permits = initialPermits;
    }

    /**
     * Acquires a permit. If no permit is available, blocks the calling thread.
     */
    public synchronized void acquire() throws InterruptedException {
        while (permits <= 0) {
            wait();
        }
        permits--;
    }

    /**
     * Releases a permit, waking up one waiting thread.
     */
    public synchronized void release() {
        permits++;
        notify(); // Wake up one waiting thread
    }

    /**
     * Returns the current number of available permits.
     */
    public synchronized int getPermits() {
        return permits;
    }
}
