package com.printscheduler.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PrintJob}.
 *
 * <p>Covers: ID sequence, immutable identity, all lifecycle transitions,
 * computed metrics, helper methods, and toString().
 */
@DisplayName("PrintJob")
class PrintJobTest {

    @BeforeEach
    void resetIdSequence() {
        PrintJob.resetIdSequence();
    }

    // ── ID sequence ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("ID sequence")
    class IdSequence {

        @Test
        @DisplayName("first job after reset gets ID 1")
        void firstJobHasId1() {
            PrintJob job = job(1, 5, false, 1, 0);
            assertThat(job.getJobId()).isEqualTo(1);
        }

        @Test
        @DisplayName("IDs are monotonically increasing")
        void idsIncrement() {
            PrintJob j1 = job(1, 1, false, 1, 0);
            PrintJob j2 = job(2, 1, false, 1, 0);
            PrintJob j3 = job(3, 1, false, 1, 0);
            assertThat(j1.getJobId()).isLessThan(j2.getJobId());
            assertThat(j2.getJobId()).isLessThan(j3.getJobId());
        }

        @Test
        @DisplayName("resetIdSequence restarts from 1")
        void resetStartsFromOne() {
            job(1, 1, false, 1, 0); // bump to 1
            PrintJob.resetIdSequence();
            PrintJob fresh = job(1, 5, false, 1, 0);
            assertThat(fresh.getJobId()).isEqualTo(1);
        }
    }

    // ── Identity / immutable fields ───────────────────────────────────────

    @Nested
    @DisplayName("Identity fields")
    class IdentityFields {

        @Test
        @DisplayName("all constructor fields are stored correctly")
        void constructorFields() {
            PrintJob job = new PrintJob(7, 12, true, 3, 5000L);
            assertThat(job.getUserId()).isEqualTo(7);
            assertThat(job.getPageCount()).isEqualTo(12);
            assertThat(job.isColor()).isTrue();
            assertThat(job.getPriority()).isEqualTo(3);
            assertThat(job.getSubmittedAt()).isEqualTo(5000L);
        }

        @Test
        @DisplayName("initial status is QUEUED")
        void initialStatusIsQueued() {
            assertThat(job(1, 1, false, 1, 0).getStatus()).isEqualTo(JobStatus.QUEUED);
        }

        @Test
        @DisplayName("initial assignedPrinterId is -1")
        void initialPrinterIdIsMinusOne() {
            assertThat(job(1, 1, false, 1, 0).getAssignedPrinterId()).isEqualTo(-1);
        }

        @Test
        @DisplayName("startedAt and completedAt are -1 initially")
        void initialTimestampsAreMinusOne() {
            PrintJob job = job(1, 5, false, 1, 0);
            assertThat(job.getStartedAt()).isEqualTo(-1);
            assertThat(job.getCompletedAt()).isEqualTo(-1);
        }
    }

    // ── Lifecycle transitions ─────────────────────────────────────────────

    @Nested
    @DisplayName("Lifecycle transitions")
    class Lifecycle {

        @Test
        @DisplayName("markPrinting → PRINTING, sets printer ID and startedAt")
        void markPrinting() {
            PrintJob job = job(1, 5, false, 1, 1000L);
            job.markPrinting(2, 2000L);

            assertThat(job.getStatus()).isEqualTo(JobStatus.PRINTING);
            assertThat(job.getAssignedPrinterId()).isEqualTo(2);
            assertThat(job.getStartedAt()).isEqualTo(2000L);
        }

        @Test
        @DisplayName("markCompleted → COMPLETED, sets completedAt")
        void markCompleted() {
            PrintJob job = job(1, 5, false, 1, 0L);
            job.markPrinting(1, 1000L);
            job.markCompleted(5000L);

            assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
            assertThat(job.getCompletedAt()).isEqualTo(5000L);
        }

        @Test
        @DisplayName("markFailed → FAILED, sets completedAt")
        void markFailed() {
            PrintJob job = job(1, 3, false, 1, 0L);
            job.markPrinting(1, 500L);
            job.markFailed(3000L);

            assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
            assertThat(job.getCompletedAt()).isEqualTo(3000L);
        }

        @Test
        @DisplayName("markCancelled → CANCELLED, sets completedAt")
        void markCancelled() {
            PrintJob job = job(1, 2, false, 1, 100L);
            job.markCancelled(800L);

            assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
            assertThat(job.getCompletedAt()).isEqualTo(800L);
        }

        @Test
        @DisplayName("setStatus directly changes status")
        void setStatus() {
            PrintJob job = job(1, 2, false, 1, 0L);
            job.setStatus(JobStatus.FAILED);
            assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        }

        @Test
        @DisplayName("setAssignedPrinterId updates printer assignment")
        void setAssignedPrinterId() {
            PrintJob job = job(1, 2, false, 1, 0L);
            job.setAssignedPrinterId(5);
            assertThat(job.getAssignedPrinterId()).isEqualTo(5);
        }
    }

    // ── Computed metrics ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Computed metrics")
    class ComputedMetrics {

        @Test
        @DisplayName("waitingTimeMs returns -1 before printing starts")
        void waitingTimeBeforePrinting() {
            PrintJob job = job(1, 5, false, 1, 1000L);
            assertThat(job.getWaitingTimeMs()).isEqualTo(-1);
        }

        @Test
        @DisplayName("waitingTimeMs = startedAt - submittedAt after printing starts")
        void waitingTimeAfterPrinting() {
            PrintJob job = job(1, 5, false, 1, 1000L);
            job.markPrinting(1, 3000L);
            assertThat(job.getWaitingTimeMs()).isEqualTo(2000L);
        }

        @Test
        @DisplayName("turnaroundTimeMs returns -1 before completion")
        void turnaroundBeforeCompletion() {
            PrintJob job = job(1, 5, false, 1, 0L);
            assertThat(job.getTurnaroundTimeMs()).isEqualTo(-1);
            job.markPrinting(1, 1000L);
            assertThat(job.getTurnaroundTimeMs()).isEqualTo(-1);
        }

        @Test
        @DisplayName("turnaroundTimeMs = completedAt - submittedAt after completion")
        void turnaroundAfterCompletion() {
            PrintJob job = job(1, 4, false, 1, 500L);
            job.markPrinting(1, 1000L);
            job.markCompleted(4500L);
            assertThat(job.getTurnaroundTimeMs()).isEqualTo(4000L);
        }

        @Test
        @DisplayName("estimatedDurationMs: monochrome = pages × msPerPage")
        void estimatedDurationMonochrome() {
            PrintJob job = job(1, 5, false, 1, 0L);
            assertThat(job.estimatedDurationMs(1000L)).isEqualTo(5000L);
        }

        @Test
        @DisplayName("estimatedDurationMs: colour = pages × msPerPage × 2")
        void estimatedDurationColor() {
            PrintJob job = job(1, 5, true, 1, 0L);
            assertThat(job.estimatedDurationMs(1000L)).isEqualTo(10_000L);
        }
    }

    // ── toString ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("toString contains key fields")
    void toStringContainsKeyFields() {
        PrintJob job = new PrintJob(3, 8, true, 2, 0L);
        String s = job.toString();
        assertThat(s).contains("user=3", "pages=8", "color=true", "priority=2", "QUEUED");
    }

    // ── Factory helper ────────────────────────────────────────────────────

    private static PrintJob job(int user, int pages, boolean color, int priority, long submittedAt) {
        return new PrintJob(user, pages, color, priority, submittedAt);
    }
}
