package com.printscheduler.model;

/**
 * Coordinates pause and resume actions across all active simulator threads.
 */
public class PauseCoordinator {
    private boolean isPaused = false;

    /**
     * Checks if the simulation is currently paused.
     * Blocks the calling thread if the pause flag is set.
     */
    public synchronized void checkPause() throws InterruptedException {
        while (isPaused) {
            wait();
        }
    }

    /**
     * Pauses the simulation. Threads calling checkPause() will block.
     */
    public synchronized void pause() {
        isPaused = true;
    }

    /**
     * Resumes the simulation. Wakes up all blocked threads.
     */
    public synchronized void resume() {
        isPaused = false;
        notifyAll(); // Wake up all threads waiting on this pause monitor
    }

    /**
     * Returns true if the simulation is currently paused.
     */
    public synchronized boolean isPaused() {
        return isPaused;
    }
}
