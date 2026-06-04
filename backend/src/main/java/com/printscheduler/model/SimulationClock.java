package com.printscheduler.model;

import java.util.function.BooleanSupplier;

/**
 * Tracks the logical simulated time, accounting for simulation speed (multiplier) and pauses.
 */
public class SimulationClock {
    private long simulatedTimeMs = 0;
    private long lastRealTimeMs = 0;
    private double speed = 1.0;
    private boolean running = false;

    /**
     * Starts the clock tracking.
     */
    public synchronized void start(double initialSpeed) {
        this.speed = initialSpeed;
        this.lastRealTimeMs = System.currentTimeMillis();
        this.running = true;
    }

    /**
     * Pauses the clock tracking.
     */
    public synchronized void pause() {
        update();
        this.running = false;
    }

    /**
     * Resumes the clock tracking.
     */
    public synchronized void resume() {
        this.lastRealTimeMs = System.currentTimeMillis();
        this.running = true;
    }

    /**
     * Stops the clock tracking.
     */
    public synchronized void stop() {
        update();
        this.running = false;
    }

    /**
     * Updates the simulation speed.
     */
    public synchronized void setSpeed(double newSpeed) {
        update();
        this.speed = newSpeed;
    }

    /**
     * Returns the simulated time elapsed in milliseconds.
     */
    public synchronized long getSimulatedTimeMs() {
        update();
        return simulatedTimeMs;
    }

    /**
     * Resets the clock to zero.
     */
    public synchronized void reset() {
        simulatedTimeMs = 0;
        lastRealTimeMs = 0;
        running = false;
        speed = 1.0;
    }

    /**
     * Updates the internal simulated time based on elapsed real-world time.
     */
    private void update() {
        if (running) {
            long now = System.currentTimeMillis();
            long deltaReal = now - lastRealTimeMs;
            simulatedTimeMs += (long) (deltaReal * speed);
            lastRealTimeMs = now;
        }
    }

    /**
     * Sleeps the calling thread for a specified amount of simulated time.
     * Respects simulation pauses and checks if the simulation is still running.
     *
     * @param simulatedMs       how many simulated milliseconds to sleep
     * @param isRunningSupplier boolean supplier returning false if simulation stopped
     * @param pauseCoordinator  coordinator to block on if paused
     */
    public void sleepSimulated(long simulatedMs, BooleanSupplier isRunningSupplier, PauseCoordinator pauseCoordinator)
            throws InterruptedException {
        long startSimTime = getSimulatedTimeMs();
        while (getSimulatedTimeMs() - startSimTime < simulatedMs) {
            if (!isRunningSupplier.getAsBoolean()) {
                throw new InterruptedException("Simulation stopped");
            }
            pauseCoordinator.checkPause();
            // Sleep for a small real-world interval (20ms) to check flags responsively
            Thread.sleep(20);
        }
    }
}
