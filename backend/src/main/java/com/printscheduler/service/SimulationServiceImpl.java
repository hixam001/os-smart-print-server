package com.printscheduler.service;

import com.printscheduler.api.dto.ConfigUpdateRequest;
import com.printscheduler.exception.SimulationAlreadyRunningException;
import com.printscheduler.exception.SimulationNotRunningException;
import com.printscheduler.exception.SimulationNotPausedException;
import com.printscheduler.model.SimulationConfig;
import com.printscheduler.model.SimulationSnapshot;
import com.printscheduler.model.SimulationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stub implementation of {@link SimulationService}.
 *
 * <hr>
 * <h3>🔧 Core Team: This is where you plug in your simulator.</h3>
 *
 * <p>Replace the stub bodies below with calls to your
 * {@code PrintServerSimulator} class.  The state machine guard-checks
 * (already implemented) can stay as-is.
 *
 * <p>Keep this class annotated with {@code @Service} so Spring autowires it.
 *
 * <hr>
 */
@Service
public class SimulationServiceImpl implements SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationServiceImpl.class);

    // ── State machine ─────────────────────────────────────────────────────
    private final AtomicReference<SimulationStatus> status =
        new AtomicReference<>(SimulationStatus.STOPPED);

    private final AtomicLong   startTimeMs        = new AtomicLong(0);
    private volatile String    currentScheduler   = "HYBRID";
    private volatile double    simulationSpeed    = 1.0;
    private volatile SimulationConfig activeConfig = null;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private com.printscheduler.websocket.SimulationBroadcaster broadcaster;

    private volatile PrintServerSimulator simulator = null;

    // =========================================================================
    //  SimulationService implementation
    // =========================================================================

    @Override
    public void start(SimulationConfig config) {
        if (!status.compareAndSet(SimulationStatus.STOPPED, SimulationStatus.RUNNING)) {
            throw new SimulationAlreadyRunningException();
        }
        this.activeConfig      = config;
        this.currentScheduler  = config.getAlgorithm();
        this.simulationSpeed   = config.getSimulationSpeed();
        this.startTimeMs.set(System.currentTimeMillis());

        log.info("Simulation STARTED | users={} printers={} queue={} algo={} speed={}x",
            config.getNumUsers(), config.getNumPrinters(),
            config.getQueueCapacity(), config.getAlgorithm(),
            config.getSimulationSpeed());

        this.simulator = new PrintServerSimulator(config, (eventType, details) -> {
            if (broadcaster != null) {
                broadcaster.publishEvent(eventType, details);
            }
        });
        this.simulator.start();
    }

    @Override
    public void pause() {
        if (!status.compareAndSet(SimulationStatus.RUNNING, SimulationStatus.PAUSED)) {
            throw new SimulationNotRunningException("pause");
        }
        log.info("Simulation PAUSED");

        if (this.simulator != null) {
            this.simulator.pause();
        }
    }

    @Override
    public void resume() {
        if (!status.compareAndSet(SimulationStatus.PAUSED, SimulationStatus.RUNNING)) {
            throw new SimulationNotPausedException();
        }
        log.info("Simulation RESUMED");

        if (this.simulator != null) {
            this.simulator.resume();
        }
    }

    @Override
    public void stop() {
        SimulationStatus prev = status.getAndSet(SimulationStatus.STOPPED);
        if (prev == SimulationStatus.STOPPED) {
            log.debug("stop() called but simulation was already stopped — no-op");
            return;
        }
        log.info("Simulation STOPPED (was {})", prev);

        if (this.simulator != null) {
            this.simulator.stop();
        }
    }

    @Override
    public void reset() {
        stop();
        startTimeMs.set(0);
        activeConfig     = null;
        currentScheduler = "HYBRID";
        simulationSpeed  = 1.0;
        log.info("Simulation RESET");

        if (this.simulator != null) {
            this.simulator.reset();
            this.simulator = null;
        }
    }

    @Override
    public void configure(ConfigUpdateRequest request) {
        if (request.getAlgorithm() != null) {
            currentScheduler = request.getAlgorithm();
            log.info("Scheduler changed to {}", currentScheduler);
        }
        if (request.getJobIntervalMs() != null) {
            log.info("Job interval changed to {} ms", request.getJobIntervalMs());
        }
        if (request.getSimulationSpeed() != null) {
            simulationSpeed = request.getSimulationSpeed();
            log.info("Simulation speed changed to {}x", simulationSpeed);
        }
        if (this.simulator != null) {
            this.simulator.configure(request);
        }
    }

    @Override
    public SimulationSnapshot getState() {
        long elapsed = startTimeMs.get() == 0 ? 0
            : System.currentTimeMillis() - startTimeMs.get();

        com.printscheduler.model.SimulationState details = null;
        if (this.simulator != null) {
            details = this.simulator.getSimulationState();
            elapsed = details.getElapsedMs();
            currentScheduler = details.getAlgorithm();
            simulationSpeed = details.getSimulationSpeed();
        }

        return new SimulationSnapshot(
            status.get(),
            elapsed,
            currentScheduler,
            simulationSpeed,
            "Simulation is " + status.get().name().toLowerCase(),
            details
        );
    }

    @Override
    public boolean isRunning() { return status.get() == SimulationStatus.RUNNING; }

    @Override
    public boolean isPaused()  { return status.get() == SimulationStatus.PAUSED; }

    @Override
    public String exportJobsCsv() {
        return this.simulator != null ? this.simulator.getDatabase().toCsv() : "";
    }
}
