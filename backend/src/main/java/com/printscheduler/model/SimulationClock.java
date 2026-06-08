package com.printscheduler.model;

import java.util.function.BooleanSupplier;

public class SimulationClock {
    private long simulatedTimeMs = 0;
    private long lastRealTimeMs = 0;
    private double speed = 1.0;
    private boolean running = false;

    public synchronized void start(double initialSpeed) {
        this.speed = initialSpeed;
        this.lastRealTimeMs = System.currentTimeMillis();
        this.running = true;
    }

    public synchronized void pause() {
        update();
        this.running = false;
    }

    public synchronized void resume() {
        this.lastRealTimeMs = System.currentTimeMillis();
        this.running = true;
    }

    public synchronized void stop() {
        update();
        this.running = false;
    }

    public synchronized void setSpeed(double newSpeed) {
        update();
        this.speed = newSpeed;
    }

    public synchronized long getSimulatedTimeMs() {
        update();
        return simulatedTimeMs;
    }

    public synchronized void reset() {
        simulatedTimeMs = 0;
        lastRealTimeMs = 0;
        running = false;
        speed = 1.0;
    }

    private void update() {
        if (running) {
            long now = System.currentTimeMillis();
            long deltaReal = now - lastRealTimeMs;
            simulatedTimeMs += (long) (deltaReal * speed);
            lastRealTimeMs = now;
        }
    }

    public void sleepSimulated(long simulatedMs, BooleanSupplier isRunningSupplier, PauseCoordinator pauseCoordinator)
            throws InterruptedException {
        long startSimTime = getSimulatedTimeMs();
        while (getSimulatedTimeMs() - startSimTime < simulatedMs) {
            if (!isRunningSupplier.getAsBoolean()) {
                throw new InterruptedException("Simulation stopped");
            }
            pauseCoordinator.checkPause();

            Thread.sleep(20);
        }
    }
}
