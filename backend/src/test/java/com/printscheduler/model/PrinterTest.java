package com.printscheduler.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link Printer}.
 *
 * <p>Covers: construction, startJob / finishJob / failJob lifecycle,
 * isJobDue(), clearError(), goOffline(), reset(), and metric accumulation.
 */
@DisplayName("Printer")
class PrinterTest {

    @BeforeEach
    void resetJobIds() {
        PrintJob.resetIdSequence();
    }

    // ── Construction ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("auto-named printer gets 'Printer-{id}' name")
        void autoName() {
            Printer p = new Printer(3);
            assertThat(p.getName()).isEqualTo("Printer-3");
            assertThat(p.getPrinterId()).isEqualTo(3);
        }

        @Test
        @DisplayName("custom-named printer stores provided name")
        void customName() {
            Printer p = new Printer(1, "LaserJet Pro");
            assertThat(p.getName()).isEqualTo("LaserJet Pro");
        }

        @Test
        @DisplayName("initial status is IDLE")
        void initialStatusIsIdle() {
            assertThat(new Printer(1).getStatus()).isEqualTo(PrinterStatus.IDLE);
        }

        @Test
        @DisplayName("initial metrics are zero")
        void initialMetricsAreZero() {
            Printer p = new Printer(1);
            assertThat(p.getJobsCompleted()).isZero();
            assertThat(p.getTotalPages()).isZero();
            assertThat(p.getTotalPrintTime()).isZero();
            assertThat(p.getCurrentJob()).isNull();
        }
    }

    // ── startJob ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("startJob")
    class StartJob {

        @Test
        @DisplayName("printer becomes BUSY and job becomes PRINTING")
        void printerBecomesBusy() {
            Printer p = printer();
            PrintJob job = job(5, false, 0L);
            p.startJob(job, 1000L, 5000L);

            assertThat(p.getStatus()).isEqualTo(PrinterStatus.BUSY);
            assertThat(p.getCurrentJob()).isSameAs(job);
            assertThat(p.getJobStartTime()).isEqualTo(1000L);
            assertThat(p.getJobFinishTime()).isEqualTo(6000L);
            assertThat(job.getStatus()).isEqualTo(JobStatus.PRINTING);
            assertThat(job.getAssignedPrinterId()).isEqualTo(p.getPrinterId());
        }
    }

    // ── finishJob ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("finishJob")
    class FinishJob {

        @Test
        @DisplayName("printer returns to IDLE and job is COMPLETED")
        void printerIdleAfterFinish() {
            Printer p = printer();
            PrintJob job = job(3, false, 0L);
            p.startJob(job, 1000L, 3000L);
            PrintJob finished = p.finishJob(4000L);

            assertThat(p.getStatus()).isEqualTo(PrinterStatus.IDLE);
            assertThat(p.getCurrentJob()).isNull();
            assertThat(finished).isSameAs(job);
            assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
            assertThat(job.getCompletedAt()).isEqualTo(4000L);
        }

        @Test
        @DisplayName("finishJob accumulates metrics correctly")
        void metricsAccumulation() {
            Printer p = printer();
            PrintJob job = job(4, false, 0L);      // 4 pages
            p.startJob(job, 1000L, 4000L);
            p.finishJob(5000L);                    // elapsed = 4000 ms

            assertThat(p.getJobsCompleted()).isEqualTo(1);
            assertThat(p.getTotalPages()).isEqualTo(4);
            assertThat(p.getTotalPrintTime()).isEqualTo(4000L);
            assertThat(p.averagePagesPerJob()).isEqualTo(4.0);
            assertThat(p.averagePrintTimeMs()).isEqualTo(4000.0);
        }

        @Test
        @DisplayName("metrics accumulate across multiple jobs")
        void metricsAccumulateAcrossJobs() {
            Printer p = printer();

            PrintJob j1 = job(2, false, 0L);
            p.startJob(j1, 0L, 2000L);
            p.finishJob(2000L);

            PrintJob j2 = job(6, false, 3000L);
            p.startJob(j2, 3000L, 6000L);
            p.finishJob(9000L);

            assertThat(p.getJobsCompleted()).isEqualTo(2);
            assertThat(p.getTotalPages()).isEqualTo(8);
            assertThat(p.averagePagesPerJob()).isEqualTo(4.0);
        }

        @Test
        @DisplayName("finishJob with no current job returns null and stays IDLE")
        void finishJobWhenIdle() {
            Printer p = printer();
            PrintJob result = p.finishJob(1000L);
            assertThat(result).isNull();
            assertThat(p.getStatus()).isEqualTo(PrinterStatus.IDLE);
        }
    }

    // ── failJob ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("failJob")
    class FailJob {

        @Test
        @DisplayName("printer goes to ERROR state, job marked FAILED")
        void printerErrorOnFail() {
            Printer p = printer();
            PrintJob job = job(2, false, 0L);
            p.startJob(job, 1000L, 2000L);
            PrintJob failed = p.failJob(1500L);

            assertThat(p.getStatus()).isEqualTo(PrinterStatus.ERROR);
            assertThat(p.getCurrentJob()).isNull();
            assertThat(failed).isSameAs(job);
            assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        }

        @Test
        @DisplayName("failJob with no current job returns null")
        void failJobWhenIdle() {
            Printer p = printer();
            assertThat(p.failJob(100L)).isNull();
            assertThat(p.getStatus()).isEqualTo(PrinterStatus.ERROR);
        }
    }

    // ── clearError / goOffline ─────────────────────────────────────────────

    @Test
    @DisplayName("clearError returns printer to IDLE")
    void clearError() {
        Printer p = printer();
        PrintJob job = job(1, false, 0L);
        p.startJob(job, 0L, 1000L);
        p.failJob(500L);
        p.clearError();
        assertThat(p.getStatus()).isEqualTo(PrinterStatus.IDLE);
    }

    @Test
    @DisplayName("goOffline sets status to OFFLINE")
    void goOffline() {
        Printer p = printer();
        p.goOffline();
        assertThat(p.getStatus()).isEqualTo(PrinterStatus.OFFLINE);
    }

    // ── isJobDue ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isJobDue")
    class IsJobDue {

        @Test
        @DisplayName("returns false when printer is IDLE")
        void falseWhenIdle() {
            assertThat(new Printer(1).isJobDue(99999L)).isFalse();
        }

        @Test
        @DisplayName("returns false before finish time")
        void falseBeforeFinishTime() {
            Printer p = printer();
            p.startJob(job(3, false, 0L), 1000L, 5000L);
            assertThat(p.isJobDue(5999L)).isFalse();
        }

        @Test
        @DisplayName("returns true at exact finish time")
        void trueAtFinishTime() {
            Printer p = printer();
            p.startJob(job(3, false, 0L), 1000L, 5000L);
            assertThat(p.isJobDue(6000L)).isTrue();
        }

        @Test
        @DisplayName("returns true after finish time")
        void trueAfterFinishTime() {
            Printer p = printer();
            p.startJob(job(3, false, 0L), 1000L, 5000L);
            assertThat(p.isJobDue(9999L)).isTrue();
        }
    }

    // ── reset ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("reset clears state and metrics")
    void resetClearsEverything() {
        Printer p = printer();
        PrintJob job = job(10, false, 0L);
        p.startJob(job, 0L, 10_000L);
        p.finishJob(10_000L);

        p.reset();

        assertThat(p.getStatus()).isEqualTo(PrinterStatus.IDLE);
        assertThat(p.getCurrentJob()).isNull();
        assertThat(p.getJobsCompleted()).isZero();
        assertThat(p.getTotalPages()).isZero();
        assertThat(p.getTotalPrintTime()).isZero();
    }

    // ── averages when no jobs ─────────────────────────────────────────────

    @Test
    @DisplayName("averagePagesPerJob returns 0 when no jobs completed")
    void avgPagesNoJobs() {
        assertThat(new Printer(1).averagePagesPerJob()).isZero();
    }

    @Test
    @DisplayName("averagePrintTimeMs returns 0 when no jobs completed")
    void avgTimeNoJobs() {
        assertThat(new Printer(1).averagePrintTimeMs()).isZero();
    }

    // ── toString ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("toString contains id, status and jobsCompleted")
    void toStringContainsKeyFields() {
        Printer p = new Printer(2);
        assertThat(p.toString()).contains("id=2", "IDLE", "jobsCompleted=0");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static Printer printer() { return new Printer(1); }

    private static PrintJob job(int pages, boolean color, long submittedAt) {
        return new PrintJob(1, pages, color, 1, submittedAt);
    }
}
