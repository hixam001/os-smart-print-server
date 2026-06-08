package com.printscheduler.service;

import com.printscheduler.api.dto.ConfigUpdateRequest;
import com.printscheduler.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class PrintServerSimulator {

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

        for (int i = 1; i <= config.getNumPrinters(); i++) {
            printers.add(new Printer(i));
        }

        this.jobsAvailable.setEventListener((type, thread, permits, waiters) ->
            publishEvent(type, java.util.Map.of(
                "thread",  thread,
                "permits", permits,
                "waiters", waiters
            ))
        );
    }

    public synchronized void start() {
        if (running.getAndSet(true)) {
            return;
        }

        PrintJob.resetIdSequence();
        database.reset();
        queue.resetMetrics();
        clock.start(config.getSimulationSpeed());

        for (Printer printer : printers) {
            PrinterThread pt = new PrinterThread(printer, this);
            printerThreads.add(pt);
            pt.start();
        }

        for (int i = 1; i <= config.getNumUsers(); i++) {
            UserThread ut = new UserThread(i, this);
            userThreads.add(ut);
            ut.start();
        }
    }

    public synchronized void pause() {
        if (running.get() && !pauseCoordinator.isPaused()) {
            pauseCoordinator.pause();
            clock.pause();
        }
    }

    public synchronized void resume() {
        if (running.get() && pauseCoordinator.isPaused()) {
            clock.resume();
            pauseCoordinator.resume();
        }
    }

    public synchronized void stop() {
        if (!running.getAndSet(false)) {
            return;
        }

        for (UserThread ut : userThreads) {
            ut.interrupt();
        }
        for (PrinterThread pt : printerThreads) {
            pt.interrupt();
        }

        userThreads.clear();
        printerThreads.clear();
        clock.stop();

        for (Printer p : printers) {
            PrintJob cur = p.getCurrentJob();
            if (cur != null) {
                cur.markCancelled(clock.getSimulatedTimeMs());
                database.record(cur);
            }
            p.reset();
        }

        queue.clear(clock.getSimulatedTimeMs());
    }

    public synchronized void reset() {
        stop();
        database.reset();
        queue.resetMetrics();
        clock.reset();
    }

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

        List<SimulationState.PrinterInfo> printerInfos = printers.stream()
                .map(p -> SimulationState.PrinterInfo.from(p, now))
                .collect(Collectors.toList());
        state.setPrinters(printerInfos);

        List<SimulationState.JobInfo> queuedJobInfos = queue.getQueuedJobsSnapshot().stream()
                .map(SimulationState.JobInfo::from)
                .collect(Collectors.toList());
        state.setQueuedJobs(queuedJobInfos);

        state.setMetrics(database.buildMetrics(now));

        state.setSemaphorePermits(jobsAvailable.getPermits());
        state.setSemaphoreWaiters(jobsAvailable.getWaiters());

        return state;
    }
}
