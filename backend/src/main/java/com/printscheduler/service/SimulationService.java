package com.printscheduler.service;

import com.printscheduler.api.dto.ConfigUpdateRequest;
import com.printscheduler.model.SimulationConfig;
import com.printscheduler.model.SimulationSnapshot;

/**
 * Contract between the REST/WebSocket API layer and the OS simulator core.
 *
 * <p>The core team implements this interface in
 * {@link SimulationServiceImpl} (already wired as a Spring {@code @Service}).
 * The API layer knows nothing about PrintJob, Semaphore, etc.
 *
 * <h3>Threading contract</h3>
 * All methods may be called from multiple threads concurrently
 * (HTTP request threads + WebSocket broadcaster thread).
 * Implementations must be thread-safe.
 */
public interface SimulationService {

    /**
     * Starts a new simulation with the given configuration.
     *
     * @param config validated simulation parameters
     * @throws com.printscheduler.exception.SimulationAlreadyRunningException if already running
     */
    void start(SimulationConfig config);

    /**
     * Pauses a running simulation. State is preserved for resume.
     *
     * @throws com.printscheduler.exception.SimulationNotRunningException if not currently running
     */
    void pause();

    /**
     * Resumes a paused simulation from where it left off.
     *
     * @throws com.printscheduler.exception.SimulationNotPausedException if not currently paused
     */
    void resume();

    /**
     * Stops the simulation and gracefully shuts down all threads.
     * Metrics are still accessible after stopping.
     */
    void stop();

    /**
     * Stops the simulation (if running) and clears all jobs, metrics and events.
     * After reset, {@link #start(SimulationConfig)} can be called again.
     */
    void reset();

    /**
     * Applies runtime configuration changes to a running or paused simulation.
     * Only non-null fields in the request are applied.
     *
     * @param request fields to update
     */
    void configure(ConfigUpdateRequest request);

    /**
     * Returns a lightweight snapshot of the current simulation state.
     * Called every 100 ms by the WebSocket broadcaster — must be fast (< 10 ms).
     *
     * @return current state; never {@code null}
     */
    SimulationSnapshot getState();

    /** @return {@code true} if the simulation is actively running */
    boolean isRunning();

    /** @return {@code true} if the simulation is paused */
    boolean isPaused();
}
