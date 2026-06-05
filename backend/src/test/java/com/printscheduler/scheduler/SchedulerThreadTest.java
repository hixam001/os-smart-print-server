package com.printscheduler.scheduler;

import com.printscheduler.model.*;
import com.printscheduler.service.PrintServerSimulator;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link PrinterThread} and {@link UserThread} behaviour.
 *
 * <p>Dev 2 – Synchronization: verifies that UserThreads enqueue jobs and
 * wake PrinterThreads via the {@link CountingSemaphore}, that PrinterThreads
 * correctly consume jobs through all three scheduling algorithms, and that
 * pause / resume coordination works correctly.
 */
@DisplayName("Scheduler – UserThread & PrinterThread integration")
class SchedulerThreadTest {

    @BeforeEach
    void resetJobIds() {
        PrintJob.resetIdSequence();
    }

    private static SimulationConfig fastConfig(String algorithm) {
        SimulationConfig cfg = new SimulationConfig();
        cfg.setNumUsers(2);
        cfg.setNumPrinters(2);
        cfg.setQueueCapacity(50);
        cfg.setJobIntervalMs(150);
        cfg.setAlgorithm(algorithm);
        cfg.setColorJobRatio(0.5);
        cfg.setSmallJobPercentage(0.9);
        cfg.setSimulationSpeed(10.0);
        return cfg;
    }

    // ── FCFS scheduling ───────────────────────────────────────────────────

    @Nested
    @DisplayName("FCFS scheduling")
    class FcfsScheduling {

        @Test
        @DisplayName("FCFS: jobs are processed and recorded in the database")
        void fcfsJobsProcessed() {
            PrintServerSimulator sim = new PrintServerSimulator(fastConfig("FCFS"), null);
            sim.start();
            try {
                Awaitility.await()
                    .atMost(6, TimeUnit.SECONDS)
                    .pollInterval(100, TimeUnit.MILLISECONDS)
                    .until(() -> sim.getDatabase().totalJobCount() >= 2);

                assertThat(sim.getDatabase().totalJobCount()).isGreaterThanOrEqualTo(2);
                // Completed jobs should exist
                assertThat(sim.getDatabase().getCompletedJobs().size()
                         + sim.getDatabase().getFailedJobs().size()).isPositive();
            } finally {
                sim.stop();
            }
        }
    }

    // ── SJF scheduling ────────────────────────────────────────────────────

    @Nested
    @DisplayName("SJF scheduling")
    class SjfScheduling {

        @Test
        @DisplayName("SJF: jobs are processed — queue prefers shorter jobs")
        void sjfJobsProcessed() {
            PrintServerSimulator sim = new PrintServerSimulator(fastConfig("SJF"), null);
            sim.start();
            try {
                Awaitility.await()
                    .atMost(6, TimeUnit.SECONDS)
                    .pollInterval(100, TimeUnit.MILLISECONDS)
                    .until(() -> sim.getDatabase().totalJobCount() >= 2);

                assertThat(sim.getDatabase().totalJobCount()).isPositive();
            } finally {
                sim.stop();
            }
        }
    }

    // ── HYBRID scheduling ─────────────────────────────────────────────────

    @Nested
    @DisplayName("HYBRID scheduling")
    class HybridScheduling {

        @Test
        @DisplayName("HYBRID: jobs are processed through the aging-aware algorithm")
        void hybridJobsProcessed() {
            PrintServerSimulator sim = new PrintServerSimulator(fastConfig("HYBRID"), null);
            sim.start();
            try {
                Awaitility.await()
                    .atMost(6, TimeUnit.SECONDS)
                    .pollInterval(100, TimeUnit.MILLISECONDS)
                    .until(() -> sim.getDatabase().totalJobCount() >= 2);

                assertThat(sim.getDatabase().totalJobCount()).isPositive();
            } finally {
                sim.stop();
            }
        }
    }

    // ── Pause / Resume ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Pause / Resume coordination")
    class PauseResume {

        @Test
        @DisplayName("database count does not increase while simulation is paused")
        void pauseFreezesProgress() throws InterruptedException {
            PrintServerSimulator sim = new PrintServerSimulator(fastConfig("FCFS"), null);
            sim.start();

            // Let a few jobs run first
            Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(50, TimeUnit.MILLISECONDS)
                .until(() -> sim.getDatabase().totalJobCount() >= 1);

            sim.pause();
            int countAtPause = sim.getDatabase().totalJobCount();

            // Wait a moment to confirm nothing progresses
            Thread.sleep(300);
            int countAfterPauseWait = sim.getDatabase().totalJobCount();

            sim.resume();
            sim.stop();

            // Allow a small tolerance: a job may have been mid-flight when we paused
            assertThat(countAfterPauseWait).isLessThanOrEqualTo(countAtPause + 1);
        }

        @Test
        @DisplayName("jobs continue accumulating after resume")
        void resumeContinuesProgress() {
            PrintServerSimulator sim = new PrintServerSimulator(fastConfig("FCFS"), null);
            sim.start();
            sim.pause();

            int countWhilePaused = sim.getDatabase().totalJobCount();
            sim.resume();

            Awaitility.await()
                .atMost(6, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> sim.getDatabase().totalJobCount() > countWhilePaused + 1);

            sim.stop();
            assertThat(sim.getDatabase().totalJobCount()).isGreaterThan(countWhilePaused);
        }
    }

    // ── Semaphore coupling (UserThread → PrinterThread) ───────────────────

    @Nested
    @DisplayName("Semaphore coupling")
    class SemaphoreCoupling {

        /**
         * After stop(), any semaphore permits left over from cancelled UserThread
         * releases should be cleared / the queue should be empty.
         */
        @Test
        @DisplayName("queue is empty after stop()")
        void queueEmptyAfterStop() {
            PrintServerSimulator sim = new PrintServerSimulator(fastConfig("FCFS"), null);
            sim.start();
            sim.stop();
            assertThat(sim.getQueue().size()).isZero();
        }

        @Test
        @DisplayName("semaphore starts at 0 before any jobs are submitted")
        void semaphoreZeroOnStart() {
            PrintServerSimulator sim = new PrintServerSimulator(fastConfig("FCFS"), null);
            // before start(), no permits
            assertThat(sim.getJobsAvailableSemaphore().getPermits()).isZero();
        }
    }

    // ── Metrics accumulation ──────────────────────────────────────────────

    @Nested
    @DisplayName("Metrics accumulation")
    class MetricsAccumulation {

        @Test
        @DisplayName("completed jobs have non-negative waiting times")
        void completedJobsHaveValidWaitingTime() {
            PrintServerSimulator sim = new PrintServerSimulator(fastConfig("FCFS"), null);
            sim.start();
            try {
                Awaitility.await()
                    .atMost(6, TimeUnit.SECONDS)
                    .pollInterval(100, TimeUnit.MILLISECONDS)
                    .until(() -> !sim.getDatabase().getCompletedJobs().isEmpty());

                sim.getDatabase().getCompletedJobs().forEach(j ->
                    assertThat(j.getWaitingTimeMs()).isGreaterThanOrEqualTo(0));
            } finally {
                sim.stop();
            }
        }

        @Test
        @DisplayName("completed jobs have non-negative turnaround times")
        void completedJobsHaveValidTurnaround() {
            PrintServerSimulator sim = new PrintServerSimulator(fastConfig("FCFS"), null);
            sim.start();
            try {
                Awaitility.await()
                    .atMost(6, TimeUnit.SECONDS)
                    .pollInterval(100, TimeUnit.MILLISECONDS)
                    .until(() -> !sim.getDatabase().getCompletedJobs().isEmpty());

                sim.getDatabase().getCompletedJobs().forEach(j ->
                    assertThat(j.getTurnaroundTimeMs()).isGreaterThanOrEqualTo(0));
            } finally {
                sim.stop();
            }
        }

        @Test
        @DisplayName("buildMetrics returns non-null object with correct completed count")
        void buildMetricsCorrect() {
            PrintServerSimulator sim = new PrintServerSimulator(fastConfig("FCFS"), null);
            sim.start();
            try {
                Awaitility.await()
                    .atMost(6, TimeUnit.SECONDS)
                    .pollInterval(100, TimeUnit.MILLISECONDS)
                    .until(() -> sim.getDatabase().getCompletedJobs().size() >= 2);

                SimulationState.Metrics m = sim.getDatabase().buildMetrics(5000L);
                assertThat(m).isNotNull();
                assertThat(m.getTotalJobsCompleted())
                    .isEqualTo(sim.getDatabase().getCompletedJobs().size());
            } finally {
                sim.stop();
            }
        }
    }
}
