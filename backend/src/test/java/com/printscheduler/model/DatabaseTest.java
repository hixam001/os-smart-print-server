package com.printscheduler.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link Database}.
 *
 * <p>Covers: record filtering, per-user indexing, metrics computation,
 * CSV generation, reset, and edge-case empty states.
 */
@DisplayName("Database")
class DatabaseTest {

    private Database db;

    @BeforeEach
    void setUp() {
        PrintJob.resetIdSequence();
        db = new Database();
    }

    // ── record ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("record()")
    class RecordTests {

        @Test
        @DisplayName("records COMPLETED job")
        void recordsCompleted() {
            PrintJob job = completedJob(1, 5, false, 0L, 1000L, 6000L);
            db.record(job);
            assertThat(db.totalJobCount()).isEqualTo(1);
            assertThat(db.getAllJobs()).containsExactly(job);
        }

        @Test
        @DisplayName("records FAILED job")
        void recordsFailed() {
            PrintJob job = failedJob(2, 3, false, 0L, 500L, 3500L);
            db.record(job);
            assertThat(db.totalJobCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("records CANCELLED job")
        void recordsCancelled() {
            PrintJob job = cancelledJob(1, 2, false, 0L, 800L);
            db.record(job);
            assertThat(db.totalJobCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("ignores QUEUED job")
        void ignoresQueued() {
            db.record(new PrintJob(1, 5, false, 1, 0L));
            assertThat(db.totalJobCount()).isZero();
        }

        @Test
        @DisplayName("ignores PRINTING job")
        void ignoresPrinting() {
            PrintJob job = new PrintJob(1, 5, false, 1, 0L);
            job.markPrinting(1, 1000L);
            db.record(job);
            assertThat(db.totalJobCount()).isZero();
        }

        @Test
        @DisplayName("ignores null without throwing")
        void ignoresNull() {
            assertThatCode(() -> db.record(null)).doesNotThrowAnyException();
            assertThat(db.totalJobCount()).isZero();
        }
    }

    // ── Filtering queries ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Filtering queries")
    class FilteringQueries {

        @BeforeEach
        void populate() {
            db.record(completedJob(1, 5, false, 0L, 1000L, 6000L));
            db.record(completedJob(1, 3, true,  0L, 500L,  3500L));
            db.record(failedJob(2, 2, false, 0L, 200L, 2200L));
            db.record(cancelledJob(3, 4, false, 0L, 300L));
        }

        @Test
        @DisplayName("getAllJobs returns all 4 terminal jobs")
        void getAllJobs() {
            assertThat(db.getAllJobs()).hasSize(4);
        }

        @Test
        @DisplayName("getCompletedJobs returns only COMPLETED")
        void getCompletedJobs() {
            List<PrintJob> completed = db.getCompletedJobs();
            assertThat(completed).hasSize(2);
            assertThat(completed).allMatch(j -> j.getStatus() == JobStatus.COMPLETED);
        }

        @Test
        @DisplayName("getFailedJobs returns only FAILED")
        void getFailedJobs() {
            List<PrintJob> failed = db.getFailedJobs();
            assertThat(failed).hasSize(1);
            assertThat(failed.getFirst().getStatus()).isEqualTo(JobStatus.FAILED);
        }

        @Test
        @DisplayName("getCancelledJobs returns only CANCELLED")
        void getCancelledJobs() {
            List<PrintJob> cancelled = db.getCancelledJobs();
            assertThat(cancelled).hasSize(1);
            assertThat(cancelled.getFirst().getStatus()).isEqualTo(JobStatus.CANCELLED);
        }

        @Test
        @DisplayName("getJobsByUser returns only that user's jobs")
        void getJobsByUser() {
            assertThat(db.getJobsByUser(1)).hasSize(2);
            assertThat(db.getJobsByUser(2)).hasSize(1);
            assertThat(db.getJobsByUser(99)).isEmpty();
        }
    }

    // ── buildMetrics ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("buildMetrics()")
    class BuildMetrics {

        @Test
        @DisplayName("returns zeroed metrics when db is empty")
        void emptyMetrics() {
            SimulationState.Metrics m = db.buildMetrics(60_000L);
            assertThat(m.getTotalJobsCompleted()).isZero();
            assertThat(m.getTotalJobsFailed()).isZero();
            assertThat(m.getTotalJobsCancelled()).isZero();
            assertThat(m.getAvgWaitingTimeMs()).isZero();
            assertThat(m.getAvgTurnaroundTimeMs()).isZero();
        }

        @Test
        @DisplayName("counts are correct with mixed statuses")
        void countsCorrect() {
            db.record(completedJob(1, 5, false, 0L, 1000L, 6000L));
            db.record(failedJob(2, 2, false, 0L, 500L, 2500L));
            db.record(cancelledJob(3, 1, false, 0L, 200L));

            SimulationState.Metrics m = db.buildMetrics(60_000L);
            assertThat(m.getTotalJobsCompleted()).isEqualTo(1);
            assertThat(m.getTotalJobsFailed()).isEqualTo(1);
            assertThat(m.getTotalJobsCancelled()).isEqualTo(1);
        }

        @Test
        @DisplayName("avgWaitingTimeMs computed from completed jobs")
        void avgWaitingTime() {
            // job1: waited 1000 ms (started 1000, submitted 0)
            db.record(completedJob(1, 5, false, 0L, 1000L, 6000L));
            // job2: waited 2000 ms
            db.record(completedJob(1, 3, false, 0L, 2000L, 5000L));

            SimulationState.Metrics m = db.buildMetrics(60_000L);
            assertThat(m.getAvgWaitingTimeMs()).isEqualTo(1500.0);
        }

        @Test
        @DisplayName("avgTurnaroundTimeMs computed from completed jobs")
        void avgTurnaroundTime() {
            // job1: turnaround = 6000 - 0 = 6000
            db.record(completedJob(1, 5, false, 0L, 1000L, 6000L));
            // job2: turnaround = 5000 - 0 = 5000
            db.record(completedJob(1, 3, false, 0L, 2000L, 5000L));

            SimulationState.Metrics m = db.buildMetrics(60_000L);
            assertThat(m.getAvgTurnaroundTimeMs()).isEqualTo(5500.0);
        }

        @Test
        @DisplayName("colorJobRatio is fraction of colour completed jobs")
        void colorJobRatio() {
            db.record(completedJob(1, 3, true,  0L, 100L, 3100L));  // colour
            db.record(completedJob(1, 3, false, 0L, 100L, 3100L));  // mono
            SimulationState.Metrics m = db.buildMetrics(60_000L);
            assertThat(m.getColorJobRatio()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("throughput is 0 for < 1 simulated minute")
        void throughputLessThanOneMinute() {
            db.record(completedJob(1, 1, false, 0L, 100L, 1100L));
            // elapsed = 30 000 ms = 0 full minutes
            SimulationState.Metrics m = db.buildMetrics(30_000L);
            // elapsedMin = 0 → raw count branch
            assertThat(m.getThroughputJobsPerMin()).isEqualTo(1);
        }

        @Test
        @DisplayName("throughput = jobs / elapsedMinutes when > 1 min")
        void throughputMoreThanOneMinute() {
            for (int i = 0; i < 6; i++) {
                db.record(completedJob(1, 1, false, 0L, 100L, 1100L));
            }
            // elapsed = 120 000 ms = 2 minutes → 6 / 2 = 3
            SimulationState.Metrics m = db.buildMetrics(120_000L);
            assertThat(m.getThroughputJobsPerMin()).isEqualTo(3);
        }
    }

    // ── completedJobsPerUser ──────────────────────────────────────────────

    @Test
    @DisplayName("completedJobsPerUser returns correct per-user counts")
    void completedJobsPerUser() {
        db.record(completedJob(1, 1, false, 0L, 100L, 1100L));
        db.record(completedJob(1, 1, false, 0L, 100L, 1100L));
        db.record(completedJob(2, 1, false, 0L, 100L, 1100L));
        db.record(failedJob(1, 1, false, 0L, 100L, 1100L));     // not completed

        var breakdown = db.completedJobsPerUser();
        assertThat(breakdown.get(1)).isEqualTo(2L);
        assertThat(breakdown.get(2)).isEqualTo(1L);
    }

    // ── toCsv ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toCsv()")
    class ToCsv {

        @Test
        @DisplayName("CSV has header row")
        void csvHasHeader() {
            String csv = db.toCsv();
            assertThat(csv).startsWith("jobId,userId,pageCount,color,priority,status,");
        }

        @Test
        @DisplayName("CSV contains one row per recorded job")
        void csvHasCorrectRowCount() {
            db.record(completedJob(1, 5, false, 0L, 1000L, 6000L));
            db.record(failedJob(2, 3, true,  0L, 500L,  3500L));
            String csv = db.toCsv();
            // 1 header + 2 data rows
            long lines = csv.lines().filter(l -> !l.isBlank()).count();
            assertThat(lines).isEqualTo(3);
        }

        @Test
        @DisplayName("CSV data rows contain job ID and status")
        void csvDataRowContents() {
            PrintJob job = completedJob(1, 5, false, 0L, 1000L, 6000L);
            db.record(job);
            String csv = db.toCsv();
            assertThat(csv).contains(String.valueOf(job.getJobId()));
            assertThat(csv).contains("COMPLETED");
        }

        @Test
        @DisplayName("empty db produces header-only CSV")
        void csvEmptyDb() {
            String csv = db.toCsv();
            long lines = csv.lines().filter(l -> !l.isBlank()).count();
            assertThat(lines).isEqualTo(1);
        }
    }

    // ── reset ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("reset clears all stored jobs and user index")
    void resetClearsAll() {
        db.record(completedJob(1, 5, false, 0L, 1000L, 6000L));
        db.record(failedJob(2, 3, false, 0L, 500L, 3500L));
        db.reset();

        assertThat(db.totalJobCount()).isZero();
        assertThat(db.getAllJobs()).isEmpty();
        assertThat(db.getJobsByUser(1)).isEmpty();
    }

    // ── getAllJobs unmodifiable ────────────────────────────────────────────

    @Test
    @DisplayName("getAllJobs returns an unmodifiable snapshot")
    void getAllJobsUnmodifiable() {
        db.record(completedJob(1, 5, false, 0L, 1000L, 6000L));
        List<PrintJob> list = db.getAllJobs();
        assertThatExceptionOfType(UnsupportedOperationException.class)
            .isThrownBy(() -> list.remove(0));
    }

    // ── toString ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("toString contains count information")
    void toStringContainsCounts() {
        db.record(completedJob(1, 5, false, 0L, 1000L, 6000L));
        String s = db.toString();
        assertThat(s).contains("total=1", "completed=1", "failed=0", "cancelled=0");
    }

    // ── Factory helpers ───────────────────────────────────────────────────

    private static PrintJob completedJob(int userId, int pages, boolean color,
                                          long submittedAt, long startedAt, long completedAt) {
        PrintJob job = new PrintJob(userId, pages, color, 1, submittedAt);
        job.markPrinting(1, startedAt);
        job.markCompleted(completedAt);
        return job;
    }

    private static PrintJob failedJob(int userId, int pages, boolean color,
                                       long submittedAt, long startedAt, long failedAt) {
        PrintJob job = new PrintJob(userId, pages, color, 1, submittedAt);
        job.markPrinting(1, startedAt);
        job.markFailed(failedAt);
        return job;
    }

    private static PrintJob cancelledJob(int userId, int pages, boolean color,
                                          long submittedAt, long cancelledAt) {
        PrintJob job = new PrintJob(userId, pages, color, 1, submittedAt);
        job.markCancelled(cancelledAt);
        return job;
    }
}
