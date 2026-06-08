package com.printscheduler.model;

public class PauseCoordinator {
    private boolean isPaused = false;

    public synchronized void checkPause() throws InterruptedException {
        while (isPaused) {
            wait();
        }
    }

    public synchronized void pause() {
        isPaused = true;
    }

    public synchronized void resume() {
        isPaused = false;
        notifyAll();
    }

    public synchronized boolean isPaused() {
        return isPaused;
    }
}
