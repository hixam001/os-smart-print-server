package com.printscheduler.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PrintQueue}.
 *
 * <p>Covers: construction validation, enqueue/dequeue with all three
 * scheduling algorithms, cancel, clear, full/empty edge cases,
 * metrics tracking, runtime algorithm switching, and thread safety.
 */
@DisplayName("PrintQueue")
class PrintQueueTest {

    private static final long NOW = 10_000L;

    @BeforeEach
    void resetIds() {
        PrintJob.resetIdSequence();
    }

    // ── Construction validation ───────────────────────────────────────────

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("capacity 0 throws IllegalArgumentException")
        void zeroCapacityThrows() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> new PrintQueue(0, "FCFS"));
        }

        @Test
        @DisplayName("null algorithm defaults to FCFS")
        void nullAlgorithmDefaultsFcfs() {
            PrintQueue q = new PrintQueue(10, null);
            assertThat(q.getAlgorithm()).isEqualTo("FCFS");
        }

        @Test
        @DisplayName("unknown algorithm defaults to FCFS")
        void unknownAlgorithmDefaultsFcfs() {
            PrintQueue q = new PrintQueue(10, "ROUND_ROBIN");
            assertThat(q.getAlgorithm()).isEqualTo("FCFS");
        }

        @Test
        @DisplayName("valid algorithms are stored case-insensitively")
        void validAlgorithmsStored() {
            assertThat(new PrintQueue(1, "sjf").getAlgorithm()).isEqualTo("SJF");
            assertThat(new PrintQueue(1, "hybrid").getAlgorithm()).isEqualTo("HYBRID");
            assertThat(new PrintQueue(1, "FCFS").getAlgorithm()).isEqualTo("FCFS");
        }

        @Test
        @DisplayName("initial metrics are zero")
        void initialMetrics() {
            PrintQueue q = new PrintQueue(5, "FCFS");
            assertThat(q.getTotalEnqueued()).isZero();
            assertThat(q.getTotalDequeued()).isZero();
            assertThat(q.getTotalRejected()).isZero();
        }

        @Test
        @DisplayName("custom aging threshold is stored")
        void customAgingThreshold() {
            PrintQueue q = new PrintQueue(10, "HYBRID", 60_000L);
            assertThat(q.getAgingThresholdMs()).isEqualTo(60_000L);
        }
    }

    // ── Enqueue / full / rejected ─────────────────────────────────────────

    @Nested
    @DisplayName("Enqueue")
    class EnqueueTests {

        @Test
        @DisplayName("enqueue returns true and increments size")
        void enqueueSuccess() {
            PrintQueue q = queue(3, "FCFS");
            assertThat(q.enqueue(job(1, false, NOW))).isTrue();
            assertThat(q.size()).isEqualTo(1);
            assertThat(q.getTotalEnqueued()).isEqualTo(1);
        }

        @Test
        @DisplayName("enqueue returns false when queue is full")
        void enqueueWhenFull() {
            PrintQueue q = queue(2, "FCFS");
            q.enqueue(job(1, false, NOW));
            q.enqueue(job(2, false, NOW));
            boolean accepted = q.enqueue(job(3, false, NOW));

            assertThat(accepted).isFalse();
            assertThat(q.size()).isEqualTo(2);
            assertThat(q.getTotalRejected()).isEqualTo(1);
        }

        @Test
        @DisplayName("enqueue null throws NullPointerException")
        void enqueueNullThrows() {
            PrintQueue q = queue(5, "FCFS");
            assertThatIllegalArgumentException().isThrownBy(() -> q.enqueue(null));
        }

        @Test
        @DisplayName("isFull and isEmpty reflect queue state")
        void fullAndEmpty() {
            PrintQueue q = queue(1, "FCFS");
            assertThat(q.isEmpty()).isTrue();
            assertThat(q.isFull()).isFalse();

            q.enqueue(job(5, false, NOW));
            assertThat(q.isEmpty()).isFalse();
            assertThat(q.isFull()).isTrue();
        }
    }

    // ── FCFS ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FCFS algorithm")
    class FcfsAlgorithm {

        @Test
        @DisplayName("dequeues jobs in submission order")
        void fcfsOrder() {
            PrintQueue q = queue(10, "FCFS");
            PrintJob j1 = job(1, false, 100L);
            PrintJob j2 = job(1, false, 200L);
            PrintJob j3 = job(1, false, 300L);
            q.enqueue(j1);
            q.enqueue(j2);
            q.enqueue(j3);

            assertThat(q.dequeue(NOW)).isSameAs(j1);
            assertThat(q.dequeue(NOW)).isSameAs(j2);
            assertThat(q.dequeue(NOW)).isSameAs(j3);
        }

        @Test
        @DisplayName("dequeue on empty queue returns null")
        void dequeueEmptyReturnsNull() {
            assertThat(queue(5, "FCFS").dequeue(NOW)).isNull();
        }

        @Test
        @DisplayName("dequeue increments totalDequeued")
        void dequeueIncrementMetrics() {
            PrintQueue q = queue(5, "FCFS");
            q.enqueue(job(1, false, NOW));
            q.dequeue(NOW);
            assertThat(q.getTotalDequeued()).isEqualTo(1);
        }
    }

    // ── SJF ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SJF algorithm")
    class SjfAlgorithm {

        @Test
        @DisplayName("dequeues shortest monochrome job first")
        void sjfMonochrome() {
            PrintQueue q = queue(10, "SJF");
            PrintJob big   = job(20, false, NOW);
            PrintJob small = job(2,  false, NOW);
            PrintJob med   = job(10, false, NOW);
            q.enqueue(big);
            q.enqueue(small);
            q.enqueue(med);

            assertThat(q.dequeue(NOW).getPageCount()).isEqualTo(2);
            assertThat(q.dequeue(NOW).getPageCount()).isEqualTo(10);
            assertThat(q.dequeue(NOW).getPageCount()).isEqualTo(20);
        }

        @Test
        @DisplayName("colour jobs count as 2× monochrome for burst estimate")
        void sjfColorWeighted() {
            PrintQueue q = queue(10, "SJF");
            // 3-page colour = 6000 ms burst
            // 5-page mono   = 5000 ms burst → shorter, comes first
            PrintJob colorJob = job(3, true,  NOW);  // 3*2*1000 = 6000
            PrintJob monoJob  = job(5, false, NOW);  // 5*1*1000 = 5000
            q.enqueue(colorJob);
            q.enqueue(monoJob);

            assertThat(q.dequeue(NOW)).isSameAs(monoJob);
            assertThat(q.dequeue(NOW)).isSameAs(colorJob);
        }
    }

    // ── HYBRID ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("HYBRID algorithm")
    class HybridAlgorithm {

        @Test
        @DisplayName("before aging threshold, behaves like SJF")
        void hybridActsAsSjfBeforeAging() {
            // Threshold = 30 000 ms; both jobs submitted at NOW, current time = NOW
            PrintQueue q = new PrintQueue(10, "HYBRID", 30_000L);
            PrintJob large = job(15, false, NOW);
            PrintJob small = job(2,  false, NOW);
            q.enqueue(large);
            q.enqueue(small);

            // Neither has waited 30 s → SJF picks the small one
            assertThat(q.dequeue(NOW)).isSameAs(small);
        }

        @Test
        @DisplayName("aged job jumps ahead of shorter fresh jobs")
        void hybridAgedJobWins() {
            long threshold = 5_000L;
            PrintQueue q = new PrintQueue(10, "HYBRID", threshold);

            // Job submitted a long time ago (waited > threshold)
            PrintJob agedJob  = job(50, false, 0L);         // submitted at t=0
            // Short fresh job submitted just now
            PrintJob freshJob = job(2,  false, NOW - 1000L); // submitted 1 s ago

            q.enqueue(agedJob);
            q.enqueue(freshJob);

            // At NOW (10000 ms), agedJob has waited 10000 ms > 5000 ms threshold
            assertThat(q.dequeue(NOW)).isSameAs(agedJob);
            assertThat(q.dequeue(NOW)).isSameAs(freshJob);
        }

        @Test
        @DisplayName("among multiple aged jobs, the longest-waiting one wins")
        void hybridLongestAgedWins() {
            long threshold = 1_000L;
            PrintQueue q = new PrintQueue(10, "HYBRID", threshold);

            PrintJob old   = job(5, false, 0L);     // waited 10000 ms
            PrintJob older = job(5, false, 2000L);   // waited 8000 ms
            // Both aged; old waited longer

            q.enqueue(older);
            q.enqueue(old);

            assertThat(q.dequeue(NOW)).isSameAs(old);
        }

        @Test
        @DisplayName("setAgingThresholdMs updates threshold at runtime")
        void setAgingThreshold() {
            PrintQueue q = new PrintQueue(10, "HYBRID", 30_000L);
            q.setAgingThresholdMs(1_000L);
            assertThat(q.getAgingThresholdMs()).isEqualTo(1_000L);
        }
    }

    // ── peek ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("peek")
    class PeekTests {

        @Test
        @DisplayName("peek returns next without removing it")
        void peekDoesNotRemove() {
            PrintQueue q = queue(5, "FCFS");
            PrintJob job = job(1, false, NOW);
            q.enqueue(job);
            assertThat(q.peek(NOW)).isSameAs(job);
            assertThat(q.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("peek on empty returns null")
        void peekEmptyReturnsNull() {
            assertThat(queue(5, "FCFS").peek(NOW)).isNull();
        }
    }

    // ── cancel ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancel")
    class CancelTests {

        @Test
        @DisplayName("cancel removes job by ID and marks it CANCELLED")
        void cancelSuccess() {
            PrintQueue q = queue(5, "FCFS");
            PrintJob job = job(3, false, NOW);
            q.enqueue(job);
            boolean result = q.cancel(job.getJobId(), NOW + 100);

            assertThat(result).isTrue();
            assertThat(q.size()).isZero();
            assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
        }

        @Test
        @DisplayName("cancel returns false for unknown job ID")
        void cancelUnknownJobReturnsFalse() {
            PrintQueue q = queue(5, "FCFS");
            assertThat(q.cancel(999L, NOW)).isFalse();
        }
    }

    // ── clear ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("clear cancels all queued jobs and empties the queue")
    void clearCancelsAll() {
        PrintQueue q = queue(10, "FCFS");
        PrintJob j1 = job(1, false, NOW);
        PrintJob j2 = job(2, false, NOW);
        q.enqueue(j1);
        q.enqueue(j2);
        q.clear(NOW + 500);

        assertThat(q.size()).isZero();
        assertThat(j1.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(j2.getStatus()).isEqualTo(JobStatus.CANCELLED);
    }

    // ── setAlgorithm ──────────────────────────────────────────────────────

    @Test
    @DisplayName("setAlgorithm changes algorithm at runtime")
    void setAlgorithmRuntime() {
        PrintQueue q = queue(10, "FCFS");
        q.setAlgorithm("SJF");
        assertThat(q.getAlgorithm()).isEqualTo("SJF");
    }

    // ── resetMetrics ─────────────────────────────────────────────────────

    @Test
    @DisplayName("resetMetrics zeroes all counters")
    void resetMetrics() {
        PrintQueue q = queue(10, "FCFS");
        q.enqueue(job(1, false, NOW));
        q.dequeue(NOW);
        q.resetMetrics();
        assertThat(q.getTotalEnqueued()).isZero();
        assertThat(q.getTotalDequeued()).isZero();
    }

    // ── getQueuedJobsSnapshot ─────────────────────────────────────────────

    @Test
    @DisplayName("getQueuedJobsSnapshot returns unmodifiable list copy")
    void snapshotIsUnmodifiable() {
        PrintQueue q = queue(5, "FCFS");
        q.enqueue(job(2, false, NOW));
        List<PrintJob> snapshot = q.getQueuedJobsSnapshot();
        assertThatExceptionOfType(UnsupportedOperationException.class)
            .isThrownBy(() -> snapshot.add(job(1, false, NOW)));
        // Original queue unaffected
        assertThat(q.size()).isEqualTo(1);
    }

    // ── Thread safety ─────────────────────────────────────────────────────

    @Test
    @DisplayName("concurrent enqueue does not exceed capacity")
    void concurrentEnqueueRespectCapacity() throws InterruptedException {
        int capacity = 20;
        int threads  = 10;
        int jobsPerThread = 5; // total attempts = 50, accepted ≤ 20

        PrintQueue q = queue(capacity, "FCFS");
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger(0);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                for (int i = 0; i < jobsPerThread; i++) {
                    if (q.enqueue(new PrintJob(1, 1, false, 1, NOW))) {
                        accepted.incrementAndGet();
                    }
                }
            });
        }

        start.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(q.size()).isLessThanOrEqualTo(capacity);
        assertThat(accepted.get()).isLessThanOrEqualTo(capacity);
    }

    @Test
    @DisplayName("concurrent enqueue/dequeue does not lose or duplicate items")
    void concurrentEnqueueDequeue() throws InterruptedException {
        int capacity  = 100;
        int producers = 4;
        int consumers = 4;
        int jobsEach  = 25; // total = 100 jobs produced exactly

        PrintQueue q = queue(capacity, "FCFS");
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger totalDequeued = new AtomicInteger(0);
        ExecutorService pool = Executors.newFixedThreadPool(producers + consumers);

        // Producers
        for (int t = 0; t < producers; t++) {
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < jobsEach; i++) {
                    q.enqueue(new PrintJob(1, 1, false, 1, NOW));
                }
            });
        }

        // Consumers
        for (int t = 0; t < consumers; t++) {
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < jobsEach; i++) {
                    PrintJob dq = null;
                    while (dq == null) {
                        dq = q.dequeue(NOW);
                        if (dq == null) Thread.yield();
                    }
                    totalDequeued.incrementAndGet();
                }
            });
        }

        start.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(totalDequeued.get()).isEqualTo(producers * jobsEach);
        assertThat(q.size()).isZero();
    }

    // ── toString ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("toString contains key fields")
    void toStringContainsKeyFields() {
        PrintQueue q = queue(5, "SJF");
        String s = q.toString();
        assertThat(s).contains("SJF", "5");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static PrintQueue queue(int capacity, String algo) {
        return new PrintQueue(capacity, algo);
    }

    private static PrintJob job(int pages, boolean color, long submittedAt) {
        return new PrintJob(1, pages, color, 1, submittedAt);
    }
}
