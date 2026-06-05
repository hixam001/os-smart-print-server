package com.printscheduler.service;

import com.printscheduler.api.dto.ConfigUpdateRequest;
import com.printscheduler.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Orchestrates the active simulation run, managing threads, synchronization tools, and state snapshots.
 */
public class PrintServerSimulator {

    /**
     * Callback interface to publish events to the WebSocket layer without circular dependencies.
     */
    public interface EventListener {
        void onEvent(String eventType, Map<String, Object> details);
    }

    private final SimulationConfig config;
    private final SimulationClock clock = new SimulationClock();
    private final PauseCoordinator pauseCoordinator = new PauseCoordinator();
    private final PrintQueue queue;
    private final Database database = new Database();
    private final List<Printer> printers = new ArrayList<>();
    private final CountingSemaphore jobsAvailable = new CountingSemaphore(0);
    private final EventListener eventListener;

    private final List<UserThread> userThreads = new ArrayList<>();
    private final List<PrinterThread> printerThreads = new ArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    public PrintServerSimulator(SimulationConfig config, EventListener eventListener) {
        this.config = config;
        this.eventListener = eventListener;
        this.queue = new PrintQueue(config.getQueueCapacity(), config.getAlgorithm());

        // Initialize printers
        for (int i = 1; i <= config.getNumPrinters(); i++) {
            printers.add(new Printer(i));
        }

        // Wire semaphore event listener — broadcasts acquire/release/wait events
        this.jobsAvailable.setEventListener((type, thread, permits, waiters) ->
            publishEvent(type, java.util.Map.of(
                "thread",  thread,
                "permits", permits,
                "waiters", waiters
            ))
        );
    }

    /**
     * Starts the simulator and spawns the User and Printer threads.
     */
    public synchronized void start() {
        if (running.getAndSet(true)) {
            return; // Already running
        }

        PrintJob.resetIdSequence();
        database.reset();
        queue.resetMetrics();
        clock.start(config.getSimulationSpeed());

        // Start printer threads
        for (Printer printer : printers) {
            PrinterThread pt = new PrinterThread(printer, this);
            printerThreads.add(pt);
            pt.start();
        }

        // Start user threads
        for (int i = 1; i <= config.getNumUsers(); i++) {
            UserThread ut = new UserThread(i, this);
            userThreads.add(ut);
            ut.start();
        }
    }

    /**
     * Pauses the simulator.
     */
    public synchronized void pause() {
        if (running.get() && !pauseCoordinator.isPaused()) {
            pauseCoordinator.pause();
            clock.pause();
        }
    }

    /**
     * Resumes the simulator.
     */
    public synchronized void resume() {
        if (running.get() && pauseCoordinator.isPaused()) {
            clock.resume();
            pauseCoordinator.resume();
        }
    }

    /**
     * Stops the simulator and interrupts all threads to shut down gracefully.
     */
    public synchronized void stop() {
        if (!running.getAndSet(false)) {
            return;
        }

        // Interrupt threads to terminate them
        for (UserThread ut : userThreads) {
            ut.interrupt();
        }
        for (PrinterThread pt : printerThreads) {
            pt.interrupt();
        }

        userThreads.clear();
        printerThreads.clear();
        clock.stop();
        
        // Return printers to IDLE or cancel active jobs
        for (Printer p : printers) {
            PrintJob cur = p.getCurrentJob();
            if (cur != null) {
                cur.markCancelled(clock.getSimulatedTimeMs());
                database.record(cur);
            }
            p.reset();
        }
        
        // Cancel all remaining jobs in queue
        queue.clear(clock.getSimulatedTimeMs());
    }

    /**
     * Stops and fully resets database, queue and clock.
     */
    public synchronized void reset() {
        stop();
        database.reset();
        queue.resetMetrics();
        clock.reset();
    }

    /**
     * Applies configuration updates at runtime.
     */
    public synchronized void configure(ConfigUpdateRequest request) {
        if (request.getAlgorithm() != null) {
            queue.setAlgorithm(request.getAlgorithm());
            config.setAlgorithm(request.getAlgorithm());
        }
        if (request.getJobIntervalMs() != null) {
            config.setJobIntervalMs(request.getJobIntervalMs());
        }
        if (request.getSimulationSpeed() != null) {
            double speed = request.getSimulationSpeed();
            clock.setSpeed(speed);
            config.setSimulationSpeed(speed);
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public SimulationConfig getConfig() {
        return config;
    }

    public SimulationClock getClock() {
        return clock;
    }

    public PauseCoordinator getPauseCoordinator() {
        return pauseCoordinator;
    }

    public PrintQueue getQueue() {
        return queue;
    }

    public Database getDatabase() {
        return database;
    }

    public List<Printer> getPrinters() {
        return printers;
    }

    public CountingSemaphore getJobsAvailableSemaphore() {
        return jobsAvailable;
    }

    public void publishEvent(String eventType, Map<String, Object> details) {
        if (eventListener != null) {
            eventListener.onEvent(eventType, details);
        }
    }

    /**
     * Builds and returns a snapshot of the current state of the simulation.
     */
    public SimulationState getSimulationState() {
        SimulationState state = new SimulationState();
        long now = clock.getSimulatedTimeMs();

        state.setStatus(pauseCoordinator.isPaused() ? SimulationStatus.PAUSED 
                : (running.get() ? SimulationStatus.RUNNING : SimulationStatus.STOPPED));
        state.setElapsedMs(now);
        state.setAlgorithm(queue.getAlgorithm());
        state.setSimulationSpeed(config.getSimulationSpeed());
        state.setTick((int) (now / 1000));

        state.setQueueSize(queue.size());
        state.setQueueCapacity(queue.getCapacity());
        state.setTotalEnqueued(queue.getTotalEnqueued());
        state.setTotalDequeued(queue.getTotalDequeued());
        state.setTotalRejected(queue.getTotalRejected());

        // Map printers to PrinterInfo
        List<SimulationState.PrinterInfo> printerInfos = printers.stream()
                .map(p -> SimulationState.PrinterInfo.from(p, now))
                .collect(Collectors.toList());
        state.setPrinters(printerInfos);

        // Map queued jobs to JobInfo
        List<SimulationState.JobInfo> queuedJobInfos = queue.getQueuedJobsSnapshot().stream()
                .map(SimulationState.JobInfo::from)
                .collect(Collectors.toList());
        state.setQueuedJobs(queuedJobInfos);

        // Recompute simulation-wide metrics
        state.setMetrics(database.buildMetrics(now));

        // Expose semaphore state for the UI semaphore deep-dive panel
        state.setSemaphorePermits(jobsAvailable.getPermits());
        state.setSemaphoreWaiters(jobsAvailable.getWaiters());

        return state;
    }
}
