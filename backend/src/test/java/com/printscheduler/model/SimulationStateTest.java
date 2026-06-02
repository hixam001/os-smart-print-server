package com.printscheduler.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link SimulationState} and its nested value types
 * {@link SimulationState.PrinterInfo}, {@link SimulationState.JobInfo},
 * and {@link SimulationState.Metrics}.
 */
@DisplayName("SimulationState")
class SimulationStateTest {

    @BeforeEach
    void resetIds() {
        PrintJob.resetIdSequence();
    }

    // ── Top-level setters / getters ───────────────────────────────────────

    @Test
    @DisplayName("all top-level fields are stored and retrieved correctly")
    void topLevelFields() {
        SimulationState state = new SimulationState();
        state.setStatus(SimulationStatus.RUNNING);
        state.setElapsedMs(12_345L);
        state.setAlgorithm("SJF");
        state.setSimulationSpeed(2.5);
        state.setTick(99);
        state.setQueueSize(7);
        state.setQueueCapacity(20);
        state.setTotalEnqueued(30L);
        state.setTotalDequeued(23L);
        state.setTotalRejected(2L);

        assertThat(state.getStatus()).isEqualTo(SimulationStatus.RUNNING);
        assertThat(state.getElapsedMs()).isEqualTo(12_345L);
        assertThat(state.getAlgorithm()).isEqualTo("SJF");
        assertThat(state.getSimulationSpeed()).isEqualTo(2.5);
        assertThat(state.getTick()).isEqualTo(99);
        assertThat(state.getQueueSize()).isEqualTo(7);
        assertThat(state.getQueueCapacity()).isEqualTo(20);
        assertThat(state.getTotalEnqueued()).isEqualTo(30L);
        assertThat(state.getTotalDequeued()).isEqualTo(23L);
        assertThat(state.getTotalRejected()).isEqualTo(2L);
    }

    @Test
    @DisplayName("default printers and queuedJobs lists are empty (not null)")
    void defaultLists() {
        SimulationState state = new SimulationState();
        assertThat(state.getPrinters()).isNotNull().isEmpty();
        assertThat(state.getQueuedJobs()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("getPrinters returns unmodifiable view")
    void printersUnmodifiable() {
        SimulationState state = new SimulationState();
        state.setPrinters(List.of());
        assertThatExceptionOfType(UnsupportedOperationException.class)
            .isThrownBy(() -> state.getPrinters().add(new SimulationState.PrinterInfo()));
    }

    @Test
    @DisplayName("getQueuedJobs returns unmodifiable view")
    void queuedJobsUnmodifiable() {
        SimulationState state = new SimulationState();
        state.setQueuedJobs(List.of());
        assertThatExceptionOfType(UnsupportedOperationException.class)
            .isThrownBy(() -> state.getQueuedJobs().add(new SimulationState.JobInfo()));
    }

    // ── PrinterInfo ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("PrinterInfo")
    class PrinterInfoTests {

        @Test
        @DisplayName("from() maps IDLE printer correctly")
        void fromIdlePrinter() {
            Printer printer = new Printer(1, "HP-1");
            SimulationState.PrinterInfo pi = SimulationState.PrinterInfo.from(printer, 5000L);

            assertThat(pi.getPrinterId()).isEqualTo(1);
            assertThat(pi.getName()).isEqualTo("HP-1");
            assertThat(pi.getStatus()).isEqualTo("IDLE");
            assertThat(pi.getCurrentJobId()).isNull();
            assertThat(pi.getCurrentJobPages()).isZero();
            assertThat(pi.getJobProgressPercent()).isZero();
            assertThat(pi.getJobsCompleted()).isZero();
            assertThat(pi.getTotalPages()).isZero();
        }

        @Test
        @DisplayName("from() maps BUSY printer with in-progress job")
        void fromBusyPrinter() {
            Printer printer = new Printer(2);
            PrintJob job = new PrintJob(1, 5, false, 1, 0L);
            printer.startJob(job, 1000L, 4000L);  // finish at 5000

            // At t=3000, job is 2000ms into a 4000ms task → 50%
            SimulationState.PrinterInfo pi = SimulationState.PrinterInfo.from(printer, 3000L);

            assertThat(pi.getStatus()).isEqualTo("BUSY");
            assertThat(pi.getCurrentJobId()).isEqualTo(job.getJobId());
            assertThat(pi.getCurrentJobPages()).isEqualTo(5);
            assertThat(pi.getJobProgressPercent()).isEqualTo(50L);
        }

        @Test
        @DisplayName("progress is capped at 100% if past finish time")
        void progressCappedAt100() {
            Printer printer = new Printer(1);
            PrintJob job = new PrintJob(1, 3, false, 1, 0L);
            printer.startJob(job, 0L, 2000L);  // finish at 2000

            // At t=5000 (past finish), progress must not exceed 100
            SimulationState.PrinterInfo pi = SimulationState.PrinterInfo.from(printer, 5000L);
            assertThat(pi.getJobProgressPercent()).isEqualTo(100L);
        }

        @Test
        @DisplayName("direct setters store values correctly")
        void directSetters() {
            SimulationState.PrinterInfo pi = new SimulationState.PrinterInfo();
            pi.setPrinterId(3);
            pi.setName("Canon");
            pi.setStatus("ERROR");
            pi.setCurrentJobId(42L);
            pi.setCurrentJobPages(8);
            pi.setJobProgressPercent(75L);
            pi.setJobsCompleted(10);
            pi.setTotalPages(200L);
            pi.setAveragePrintTimeMs(3000.0);

            assertThat(pi.getPrinterId()).isEqualTo(3);
            assertThat(pi.getName()).isEqualTo("Canon");
            assertThat(pi.getStatus()).isEqualTo("ERROR");
            assertThat(pi.getCurrentJobId()).isEqualTo(42L);
            assertThat(pi.getCurrentJobPages()).isEqualTo(8);
            assertThat(pi.getJobProgressPercent()).isEqualTo(75L);
            assertThat(pi.getJobsCompleted()).isEqualTo(10);
            assertThat(pi.getTotalPages()).isEqualTo(200L);
            assertThat(pi.getAveragePrintTimeMs()).isEqualTo(3000.0);
        }
    }

    // ── JobInfo ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("JobInfo")
    class JobInfoTests {

        @Test
        @DisplayName("from() maps QUEUED job correctly")
        void fromQueuedJob() {
            PrintJob job = new PrintJob(3, 8, true, 2, 500L);
            SimulationState.JobInfo ji = SimulationState.JobInfo.from(job);

            assertThat(ji.getJobId()).isEqualTo(job.getJobId());
            assertThat(ji.getUserId()).isEqualTo(3);
            assertThat(ji.getPageCount()).isEqualTo(8);
            assertThat(ji.isColor()).isTrue();
            assertThat(ji.getPriority()).isEqualTo(2);
            assertThat(ji.getStatus()).isEqualTo("QUEUED");
            assertThat(ji.getSubmittedAt()).isEqualTo(500L);
            assertThat(ji.getWaitingTimeMs()).isEqualTo(-1L);
            assertThat(ji.getTurnaroundTimeMs()).isEqualTo(-1L);
        }

        @Test
        @DisplayName("from() maps COMPLETED job with timing metrics")
        void fromCompletedJob() {
            PrintJob job = new PrintJob(1, 4, false, 1, 0L);
            job.markPrinting(1, 1000L);
            job.markCompleted(5000L);
            SimulationState.JobInfo ji = SimulationState.JobInfo.from(job);

            assertThat(ji.getStatus()).isEqualTo("COMPLETED");
            assertThat(ji.getWaitingTimeMs()).isEqualTo(1000L);
            assertThat(ji.getTurnaroundTimeMs()).isEqualTo(5000L);
        }

        @Test
        @DisplayName("direct setters store values correctly")
        void directSetters() {
            SimulationState.JobInfo ji = new SimulationState.JobInfo();
            ji.setJobId(7L);
            ji.setUserId(2);
            ji.setPageCount(12);
            ji.setColor(true);
            ji.setPriority(3);
            ji.setStatus("FAILED");
            ji.setSubmittedAt(1000L);
            ji.setWaitingTimeMs(500L);
            ji.setTurnaroundTimeMs(5500L);

            assertThat(ji.getJobId()).isEqualTo(7L);
            assertThat(ji.getUserId()).isEqualTo(2);
            assertThat(ji.getPageCount()).isEqualTo(12);
            assertThat(ji.isColor()).isTrue();
            assertThat(ji.getPriority()).isEqualTo(3);
            assertThat(ji.getStatus()).isEqualTo("FAILED");
            assertThat(ji.getSubmittedAt()).isEqualTo(1000L);
            assertThat(ji.getWaitingTimeMs()).isEqualTo(500L);
            assertThat(ji.getTurnaroundTimeMs()).isEqualTo(5500L);
        }
    }

    // ── Metrics ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Metrics")
    class MetricsTests {

        @Test
        @DisplayName("default Metrics object is zeroed")
        void defaultMetrics() {
            SimulationState.Metrics m = new SimulationState.Metrics();
            assertThat(m.getTotalJobsCompleted()).isZero();
            assertThat(m.getTotalJobsFailed()).isZero();
            assertThat(m.getTotalJobsCancelled()).isZero();
            assertThat(m.getAvgWaitingTimeMs()).isZero();
            assertThat(m.getAvgTurnaroundTimeMs()).isZero();
            assertThat(m.getAvgPageCount()).isZero();
            assertThat(m.getThroughputJobsPerMin()).isZero();
            assertThat(m.getColorJobRatio()).isZero();
        }

        @Test
        @DisplayName("all setters store values correctly")
        void allSetters() {
            SimulationState.Metrics m = new SimulationState.Metrics();
            m.setTotalJobsCompleted(10);
            m.setTotalJobsFailed(2);
            m.setTotalJobsCancelled(1);
            m.setAvgWaitingTimeMs(1500.0);
            m.setAvgTurnaroundTimeMs(4500.0);
            m.setAvgPageCount(6.5);
            m.setThroughputJobsPerMin(5L);
            m.setColorJobRatio(0.4);

            assertThat(m.getTotalJobsCompleted()).isEqualTo(10);
            assertThat(m.getTotalJobsFailed()).isEqualTo(2);
            assertThat(m.getTotalJobsCancelled()).isEqualTo(1);
            assertThat(m.getAvgWaitingTimeMs()).isEqualTo(1500.0);
            assertThat(m.getAvgTurnaroundTimeMs()).isEqualTo(4500.0);
            assertThat(m.getAvgPageCount()).isEqualTo(6.5);
            assertThat(m.getThroughputJobsPerMin()).isEqualTo(5L);
            assertThat(m.getColorJobRatio()).isEqualTo(0.4);
        }

        @Test
        @DisplayName("setMetrics on SimulationState stores and retrieves it")
        void setMetricsOnState() {
            SimulationState state = new SimulationState();
            SimulationState.Metrics m = new SimulationState.Metrics();
            m.setTotalJobsCompleted(42);
            state.setMetrics(m);
            assertThat(state.getMetrics().getTotalJobsCompleted()).isEqualTo(42);
        }
    }
}
