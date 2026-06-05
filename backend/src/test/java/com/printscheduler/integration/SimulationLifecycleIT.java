package com.printscheduler.integration;

import com.printscheduler.model.*;
import com.printscheduler.service.PrintServerSimulator;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Full-lifecycle integration tests for the simulation layer.
 *
 * <p>Dev 3 – Simulator orchestration: exercises the complete
 * {@code start → run → pause → resume → stop → reset} lifecycle using a real
 * {@link PrintServerSimulator} (no Spring context needed).
 *
 * <p>Also verifies the {@code SimulationState} snapshot that the REST layer
 * serialises and the {@link Database} CSV export that the export endpoint serves.
 */
@DisplayName("Simulation – Full lifecycle integration")
class SimulationLifecycleIT {

    @BeforeEach
    void resetIds() {
        PrintJob.resetIdSequence();
    }

    private static SimulationConfig config() {
        SimulationConfig cfg = new SimulationConfig();
        cfg.setNumUsers(3);
        cfg.setNumPrinters(2);
        cfg.setQueueCapacity(30);
        cfg.setJobIntervalMs(200);
        cfg.setAlgorithm("HYBRID");
        cfg.setColorJobRatio(0.5);
        cfg.setSmallJobPercentage(0.8);
        cfg.setSimulationSpeed(10.0);
        return cfg;
    }

    // ── Full lifecycle ────────────────────────────────────────────────────

    @Test
    @DisplayName("start → stop: at least 1 job completes within 5 s")
    void startStop_jobsComplete() {
        PrintServerSimulator sim = new PrintServerSimulator(config(), null);
        sim.start();
        try {
            Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> sim.getDatabase().totalJobCount() >= 1);
        } finally {
            sim.stop();
        }
        assertThat(sim.getDatabase().totalJobCount()).isPositive();
    }

    @Test
    @DisplayName("start → pause → resume → stop: jobs keep accumulating after resume")
    void pauseResume_continuesAfterResume() {
        PrintServerSimulator sim = new PrintServerSimulator(config(), null);
        sim.start();

        // wait for some activity
        Awaitility.await()
            .atMost(4, TimeUnit.SECONDS)
            .pollInterval(80, TimeUnit.MILLISECONDS)
            .until(() -> sim.getDatabase().totalJobCount() >= 1);

        sim.pause();
        int countAtPause = sim.getDatabase().totalJobCount();
        sim.resume();

        // After resume, more jobs should be recorded
        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .until(() -> sim.getDatabase().totalJobCount() > countAtPause + 1);

        sim.stop();
        assertThat(sim.getDatabase().totalJobCount()).isGreaterThan(countAtPause);
    }

    @Test
    @DisplayName("reset() after a run clears the database for a fresh start")
    void reset_clearsDatabaseAndAllowsRestart() {
        PrintServerSimulator sim = new PrintServerSimulator(config(), null);
        sim.start();

        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .until(() -> sim.getDatabase().totalJobCount() >= 2);

        sim.reset();
        assertThat(sim.getDatabase().totalJobCount()).isZero();
        assertThat(sim.isRunning()).isFalse();
    }

    // ── SimulationState snapshot ──────────────────────────────────────────

    @Nested
    @DisplayName("SimulationState snapshot")
    class StateSnapshot {

        @Test
        @DisplayName("snapshot contains correct number of printers")
        void snapshotPrinterList() {
            SimulationConfig cfg = config();
            cfg.setNumPrinters(3);
            PrintServerSimulator sim = new PrintServerSimulator(cfg, null);
            sim.start();
            try {
                SimulationState state = sim.getSimulationState();
                assertThat(state.getPrinters()).hasSize(3);
            } finally {
                sim.stop();
            }
        }

        @Test
        @DisplayName("snapshot queue capacity matches config")
        void snapshotQueueCapacity() {
            SimulationConfig cfg = config();
            cfg.setQueueCapacity(25);
            PrintServerSimulator sim = new PrintServerSimulator(cfg, null);
            sim.start();
            try {
                assertThat(sim.getSimulationState().getQueueCapacity()).isEqualTo(25);
            } finally {
                sim.stop();
            }
        }

        @Test
        @DisplayName("snapshot metrics update as jobs complete")
        void snapshotMetricsUpdate() {
            PrintServerSimulator sim = new PrintServerSimulator(config(), null);
            sim.start();
            try {
                Awaitility.await()
                    .atMost(6, TimeUnit.SECONDS)
                    .pollInterval(100, TimeUnit.MILLISECONDS)
                    .until(() -> sim.getSimulationState()
                        .getMetrics().getTotalJobsCompleted() >= 2);

                assertThat(sim.getSimulationState()
                    .getMetrics().getTotalJobsCompleted()).isGreaterThanOrEqualTo(2);
            } finally {
                sim.stop();
            }
        }

        @Test
        @DisplayName("snapshot elapsedMs grows over time")
        void snapshotElapsedMsGrows() throws InterruptedException {
            PrintServerSimulator sim = new PrintServerSimulator(config(), null);
            sim.start();
            try {
                long t1 = sim.getSimulationState().getElapsedMs();
                Thread.sleep(200);
                long t2 = sim.getSimulationState().getElapsedMs();
                assertThat(t2).isGreaterThan(t1);
            } finally {
                sim.stop();
            }
        }

        @Test
        @DisplayName("PAUSED snapshot status when paused")
        void snapshotPausedStatus() {
            PrintServerSimulator sim = new PrintServerSimulator(config(), null);
            sim.start();
            sim.pause();
            try {
                assertThat(sim.getSimulationState().getStatus())
                    .isEqualTo(SimulationStatus.PAUSED);
            } finally {
                sim.resume();
                sim.stop();
            }
        }

        @Test
        @DisplayName("queued jobs in snapshot have correct fields")
        void snapshotQueuedJobFields() {
            // Directly enqueue a job so the snapshot can pick it up deterministically,
            // without depending on paused threads filling the queue.
            PrintServerSimulator sim = new PrintServerSimulator(config(), null);
            PrintJob job = new PrintJob(1, 5, false, 3, 0L);
            boolean accepted = sim.getQueue().enqueue(job);
            assertThat(accepted).isTrue();

            SimulationState state = sim.getSimulationState();
            assertThat(state.getQueuedJobs()).hasSize(1);

            SimulationState.JobInfo info = state.getQueuedJobs().get(0);
            assertThat(info.getPageCount()).isEqualTo(5);
            assertThat(info.getUserId()).isEqualTo(1);
            assertThat(info.getJobId()).isPositive();
            assertThat(info.getPriority()).isEqualTo(3);
        }
    }

    // ── Database CSV export ───────────────────────────────────────────────

    @Nested
    @DisplayName("Database CSV export")
    class CsvExport {

        @Test
        @DisplayName("toCsv() returns header line when database is empty")
        void csvEmptyHasHeader() {
            PrintServerSimulator sim = new PrintServerSimulator(config(), null);
            String csv = sim.getDatabase().toCsv();
            assertThat(csv).startsWith("jobId,userId");
        }

        @Test
        @DisplayName("toCsv() contains one data row per completed/failed/cancelled job")
        void csvRowsMatchJobCount() {
            PrintServerSimulator sim = new PrintServerSimulator(config(), null);
            sim.start();
            try {
                Awaitility.await()
                    .atMost(6, TimeUnit.SECONDS)
                    .pollInterval(100, TimeUnit.MILLISECONDS)
                    .until(() -> sim.getDatabase().totalJobCount() >= 3);
            } finally {
                sim.stop();
            }

            String csv = sim.getDatabase().toCsv();
            // count newlines (1 header + N data rows)
            long lines = csv.lines().filter(l -> !l.isBlank()).count();
            assertThat(lines).isEqualTo(sim.getDatabase().totalJobCount() + 1L);
        }
    }

    // ── Event delivery ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Event delivery")
    class EventDelivery {

        @Test
        @DisplayName("JOB_SUBMITTED events are fired")
        void jobSubmittedEventsAreFired() {
            AtomicInteger submitCount = new AtomicInteger(0);
            PrintServerSimulator sim = new PrintServerSimulator(config(),
                (type, details) -> {
                    if ("JOB_SUBMITTED".equals(type)) submitCount.incrementAndGet();
                });
            sim.start();
            try {
                Awaitility.await()
                    .atMost(5, TimeUnit.SECONDS)
                    .pollInterval(50, TimeUnit.MILLISECONDS)
                    .until(() -> submitCount.get() >= 2);

                assertThat(submitCount.get()).isGreaterThanOrEqualTo(2);
            } finally {
                sim.stop();
            }
        }

        @Test
        @DisplayName("PRINT_COMPLETED events are fired")
        void printCompletedEventsAreFired() {
            AtomicInteger completedCount = new AtomicInteger(0);
            PrintServerSimulator sim = new PrintServerSimulator(config(),
                (type, details) -> {
                    if ("PRINT_COMPLETED".equals(type)) completedCount.incrementAndGet();
                });
            sim.start();
            try {
                Awaitility.await()
                    .atMost(8, TimeUnit.SECONDS)
                    .pollInterval(100, TimeUnit.MILLISECONDS)
                    .until(() -> completedCount.get() >= 1);

                assertThat(completedCount.get()).isPositive();
            } finally {
                sim.stop();
            }
        }
    }

    // ── Algorithm switching at runtime ────────────────────────────────────

    @Test
    @DisplayName("switching algorithm at runtime does not stop job processing")
    void runtimeAlgorithmSwitch_continuesProcessing() {
        PrintServerSimulator sim = new PrintServerSimulator(config(), null);
        sim.start();
        try {
            Awaitility.await()
                .atMost(4, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> sim.getDatabase().totalJobCount() >= 1);

            com.printscheduler.api.dto.ConfigUpdateRequest req =
                new com.printscheduler.api.dto.ConfigUpdateRequest();
            req.setAlgorithm("FCFS");
            sim.configure(req);

            int before = sim.getDatabase().totalJobCount();

            Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> sim.getDatabase().totalJobCount() > before + 1);

            assertThat(sim.getDatabase().totalJobCount()).isGreaterThan(before);
        } finally {
            sim.stop();
        }
    }
}
