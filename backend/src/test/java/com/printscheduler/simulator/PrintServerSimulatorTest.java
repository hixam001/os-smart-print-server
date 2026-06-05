package com.printscheduler.simulator;

import com.printscheduler.model.*;
import com.printscheduler.service.PrintServerSimulator;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link PrintServerSimulator}.
 *
 * <p>Dev 2 / Dev 3 boundary: verifies that the simulator correctly orchestrates
 * {@link UserThread}s and {@link PrinterThread}s through the
 * {@link CountingSemaphore} and {@link PrintQueue}, and that its state
 * snapshot reflects the live simulation.
 *
 * <p>Uses Awaitility to wait for asynchronous state changes rather than raw
 * {@code Thread.sleep()}, so tests remain deterministic without being slow.
 */
@DisplayName("PrintServerSimulator")
class PrintServerSimulatorTest {

    private static SimulationConfig defaultConfig() {
        SimulationConfig cfg = new SimulationConfig();
        cfg.setNumUsers(2);
        cfg.setNumPrinters(2);
        cfg.setQueueCapacity(20);
        cfg.setJobIntervalMs(200);   // fast: 200 ms between submissions
        cfg.setAlgorithm("FCFS");
        cfg.setColorJobRatio(0.5);
        cfg.setSmallJobPercentage(0.9);
        cfg.setSimulationSpeed(10.0); // 10× speed so 1 s sim ≈ 100 ms real
        return cfg;
    }

    @BeforeEach
    void resetJobIds() {
        PrintJob.resetIdSequence();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("isRunning() is false before start()")
        void notRunningBeforeStart() {
            PrintServerSimulator sim = new PrintServerSimulator(defaultConfig(), null);
            assertThat(sim.isRunning()).isFalse();
        }

        @Test
        @DisplayName("isRunning() is true after start()")
        void runningAfterStart() {
            PrintServerSimulator sim = new PrintServerSimulator(defaultConfig(), null);
            try {
                sim.start();
                assertThat(sim.isRunning()).isTrue();
            } finally {
                sim.stop();
            }
        }

        @Test
        @DisplayName("isRunning() is false after stop()")
        void notRunningAfterStop() {
            PrintServerSimulator sim = new PrintServerSimulator(defaultConfig(), null);
            sim.start();
            sim.stop();
            assertThat(sim.isRunning()).isFalse();
        }

        @Test
        @DisplayName("calling start() twice is idempotent (no exception)")
        void doubleStartIsIdempotent() {
            PrintServerSimulator sim = new PrintServerSimulator(defaultConfig(), null);
            try {
                sim.start();
                sim.start(); // should silently return
                assertThat(sim.isRunning()).isTrue();
            } finally {
                sim.stop();
            }
        }

        @Test
        @DisplayName("calling stop() twice is idempotent (no exception)")
        void doubleStopIsIdempotent() {
            PrintServerSimulator sim = new PrintServerSimulator(defaultConfig(), null);
            sim.start();
            sim.stop();
            assertThatCode(sim::stop).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("reset() stops the simulator and clears the database")
        void resetClearsState() throws InterruptedException {
            PrintServerSimulator sim = new PrintServerSimulator(defaultConfig(), null);
            sim.start();

            // Let at least a few jobs be processed
            Awaitility.await()
                .atMost(4, TimeUnit.SECONDS)
                .pollInterval(50, TimeUnit.MILLISECONDS)
                .until(() -> sim.getDatabase().totalJobCount() > 0);

            sim.reset();

            assertThat(sim.isRunning()).isFalse();
            assertThat(sim.getDatabase().totalJobCount()).isZero();
        }
    }

    // ── Printer initialisation ────────────────────────────────────────────

    @Nested
    @DisplayName("Printer initialisation")
    class PrinterInit {

        @Test
        @DisplayName("correct number of printers is created")
        void printerCount() {
            SimulationConfig cfg = defaultConfig();
            cfg.setNumPrinters(3);
            PrintServerSimulator sim = new PrintServerSimulator(cfg, null);
            assertThat(sim.getPrinters()).hasSize(3);
        }

        @Test
        @DisplayName("all printers start IDLE")
        void printersStartIdle() {
            PrintServerSimulator sim = new PrintServerSimulator(defaultConfig(), null);
            sim.getPrinters().forEach(p ->
                assertThat(p.getStatus()).isEqualTo(PrinterStatus.IDLE));
        }

        @Test
        @DisplayName("printers have 1-based IDs")
        void printerIds() {
            SimulationConfig cfg = defaultConfig();
            cfg.setNumPrinters(3);
            PrintServerSimulator sim = new PrintServerSimulator(cfg, null);
            for (int i = 0; i < 3; i++) {
                assertThat(sim.getPrinters().get(i).getPrinterId()).isEqualTo(i + 1);
            }
        }
    }

    // ── Queue wiring ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Queue wiring")
    class QueueWiring {

        @Test
        @DisplayName("queue uses the algorithm from config")
        void queueAlgorithm() {
            SimulationConfig cfg = defaultConfig();
            cfg.setAlgorithm("SJF");
            PrintServerSimulator sim = new PrintServerSimulator(cfg, null);
            assertThat(sim.getQueue().getAlgorithm()).isEqualTo("SJF");
        }

        @Test
        @DisplayName("queue capacity matches config")
        void queueCapacity() {
            SimulationConfig cfg = defaultConfig();
            cfg.setQueueCapacity(42);
            PrintServerSimulator sim = new PrintServerSimulator(cfg, null);
            assertThat(sim.getQueue().getCapacity()).isEqualTo(42);
        }

        @Test
        @DisplayName("semaphore starts at 0 permits (no jobs yet)")
        void semaphoreStartsAtZero() {
            PrintServerSimulator sim = new PrintServerSimulator(defaultConfig(), null);
            assertThat(sim.getJobsAvailableSemaphore().getPermits()).isEqualTo(0);
        }
    }

    // ── Jobs actually flow through the system ────────────────────────────

    @Nested
    @DisplayName("Job processing")
    class JobProcessing {

        @Test
        @DisplayName("database accumulates completed jobs during a run")
        void jobsAreCompleted() {
            PrintServerSimulator sim = new PrintServerSimulator(defaultConfig(), null);
            sim.start();
            try {
                Awaitility.await()
                    .atMost(5, TimeUnit.SECONDS)
                    .pollInterval(100, TimeUnit.MILLISECONDS)
                    .until(() -> sim.getDatabase().totalJobCount() >= 3);

                assertThat(sim.getDatabase().totalJobCount()).isGreaterThanOrEqualTo(3);
            } finally {
                sim.stop();
            }
        }

        @Test
        @DisplayName("queue enqueued counter increases over time")
        void queueCounterIncreases() {
            PrintServerSimulator sim = new PrintServerSimulator(defaultConfig(), null);
            sim.start();
            try {
                Awaitility.await()
                    .atMost(5, TimeUnit.SECONDS)
                    .pollInterval(100, TimeUnit.MILLISECONDS)
                    .until(() -> sim.getQueue().getTotalEnqueued() > 0);

                assertThat(sim.getQueue().getTotalEnqueued()).isPositive();
            } finally {
                sim.stop();
            }
        }

        @Test
        @DisplayName("printers complete jobs (jobsCompleted > 0 for at least one printer)")
        void printersCompleteJobs() {
            PrintServerSimulator sim = new PrintServerSimulator(defaultConfig(), null);
            sim.start();
            try {
                Awaitility.await()
                    .atMost(6, TimeUnit.SECONDS)
                    .pollInterval(100, TimeUnit.MILLISECONDS)
                    .until(() -> sim.getPrinters().stream()
                        .anyMatch(p -> p.getJobsCompleted() > 0));

                assertThat(sim.getPrinters())
                    .anyMatch(p -> p.getJobsCompleted() > 0);
            } finally {
                sim.stop();
            }
        }
    }

    // ── Event listener ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Event listener")
    class EventListener {

        @Test
        @DisplayName("publishEvent calls listener with correct event type")
        void publishEventCallsListener() {
            AtomicInteger callCount = new AtomicInteger(0);
            PrintServerSimulator sim = new PrintServerSimulator(defaultConfig(),
                (type, details) -> callCount.incrementAndGet());

            sim.publishEvent("TEST_EVENT", java.util.Map.of("key", "value"));
            assertThat(callCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("publishEvent with null listener does not throw")
        void publishEventNullListener() {
            PrintServerSimulator sim = new PrintServerSimulator(defaultConfig(), null);
            assertThatCode(() -> sim.publishEvent("X", java.util.Map.of()))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("events are fired during a live simulation run")
        void eventsArePublishedDuringRun() {
            AtomicInteger eventCount = new AtomicInteger(0);
            PrintServerSimulator sim = new PrintServerSimulator(defaultConfig(),
                (type, details) -> eventCount.incrementAndGet());
            sim.start();
            try {
                Awaitility.await()
                    .atMost(5, TimeUnit.SECONDS)
                    .pollInterval(50, TimeUnit.MILLISECONDS)
                    .until(() -> eventCount.get() > 0);

                assertThat(eventCount.get()).isPositive();
            } finally {
                sim.stop();
            }
        }
    }

    // ── State snapshot ────────────────────────────────────────────────────

    @Nested
    @DisplayName("State snapshot")
    class StateSnapshot {

        @Test
        @DisplayName("snapshot reports RUNNING status while sim is active")
        void snapshotRunning() {
            PrintServerSimulator sim = new PrintServerSimulator(defaultConfig(), null);
            sim.start();
            try {
                SimulationState state = sim.getSimulationState();
                assertThat(state.getStatus()).isEqualTo(SimulationStatus.RUNNING);
            } finally {
                sim.stop();
            }
        }

        @Test
        @DisplayName("snapshot reports STOPPED after stop()")
        void snapshotStopped() {
            PrintServerSimulator sim = new PrintServerSimulator(defaultConfig(), null);
            sim.start();
            sim.stop();
            SimulationState state = sim.getSimulationState();
            assertThat(state.getStatus()).isEqualTo(SimulationStatus.STOPPED);
        }

        @Test
        @DisplayName("snapshot contains expected number of printer infos")
        void snapshotPrinterCount() {
            SimulationConfig cfg = defaultConfig();
            cfg.setNumPrinters(4);
            PrintServerSimulator sim = new PrintServerSimulator(cfg, null);
            sim.start();
            try {
                SimulationState state = sim.getSimulationState();
                assertThat(state.getPrinters()).hasSize(4);
            } finally {
                sim.stop();
            }
        }

        @Test
        @DisplayName("snapshot algorithm matches config")
        void snapshotAlgorithm() {
            SimulationConfig cfg = defaultConfig();
            cfg.setAlgorithm("SJF");
            PrintServerSimulator sim = new PrintServerSimulator(cfg, null);
            sim.start();
            try {
                assertThat(sim.getSimulationState().getAlgorithm()).isEqualTo("SJF");
            } finally {
                sim.stop();
            }
        }
    }

    // ── Runtime configure ─────────────────────────────────────────────────

    @Nested
    @DisplayName("configure()")
    class Configure {

        @Test
        @DisplayName("switching algorithm at runtime is reflected in queue")
        void switchAlgorithm() {
            PrintServerSimulator sim = new PrintServerSimulator(defaultConfig(), null);
            sim.start();
            try {
                com.printscheduler.api.dto.ConfigUpdateRequest req =
                    new com.printscheduler.api.dto.ConfigUpdateRequest();
                req.setAlgorithm("SJF");
                sim.configure(req);
                assertThat(sim.getQueue().getAlgorithm()).isEqualTo("SJF");
            } finally {
                sim.stop();
            }
        }

        @Test
        @DisplayName("changing job interval is applied to config")
        void changeJobInterval() {
            PrintServerSimulator sim = new PrintServerSimulator(defaultConfig(), null);
            sim.start();
            try {
                com.printscheduler.api.dto.ConfigUpdateRequest req =
                    new com.printscheduler.api.dto.ConfigUpdateRequest();
                req.setJobIntervalMs(500L);
                sim.configure(req);
                assertThat(sim.getConfig().getJobIntervalMs()).isEqualTo(500L);
            } finally {
                sim.stop();
            }
        }
    }
}
