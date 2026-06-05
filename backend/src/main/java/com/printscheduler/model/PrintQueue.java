package com.printscheduler.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Bounded, algorithm-aware print queue.
 *
 * <h3>Supported scheduling algorithms</h3>
 * <ul>
 *   <li><b>FCFS</b> – First-Come, First-Served: jobs are dispatched in
 *       the order they arrived (FIFO on {@code submittedAt}).</li>
 *   <li><b>SJF</b> – Shortest Job First: the job with the fewest pages
 *       (× colour multiplier) is dispatched next.</li>
 *   <li><b>HYBRID</b> – Combines SJF with a starvation-prevention boost:
 *       any job that has waited longer than {@code agingThresholdMs} has
 *       its effective priority raised so it cannot be starved indefinitely.</li>
 * </ul>
 *
 * <h3>Capacity</h3>
 * The queue rejects new jobs when it is full; callers receive {@code false}
 * from {@link #enqueue(PrintJob)} and must handle the rejection.
 *
 * <h3>Thread-safety</h3>
 * All public methods are guarded by an internal {@link ReentrantLock}.
 * The simulator may enqueue jobs from a user-thread while the printer
 * dispatcher dequeues from a scheduler thread.
 *
 * <h3>Metrics</h3>
 * The queue tracks total jobs enqueued, dequeued, and rejected for
 * dashboard display.
 */
public class PrintQueue {

    // ── Constants ─────────────────────────────────────────────────────────

    /** Default aging threshold for HYBRID mode (30 simulation-seconds). */
    public static final long DEFAULT_AGING_THRESHOLD_MS = 30_000L;

    /**
     * Milliseconds of estimated print time per monochrome page.
     * 3 000 ms ≈ 20 pages/min — realistic for a mid-range laser printer.
     * Colour jobs get a 1.5× multiplier applied in {@link PrintJob#estimatedDurationMs}.
     */
    public static final long MS_PER_PAGE = 3_000L;

    /**
     * One-time job computation delay before printing starts.
     * Simulates the printer receiving the job, processing fonts/graphics,
     * and warming up the fuser — typically 2–5 s on a real device.
     */
    public static final long MS_COMPUTE_DELAY = 2_500L;

    // ── Configuration ─────────────────────────────────────────────────────
    private final int    capacity;
    private       String algorithm;        // "FCFS" | "SJF" | "HYBRID"
    private       long   agingThresholdMs; // HYBRID: starvation threshold

    // ── Internal state ────────────────────────────────────────────────────
    private final List<PrintJob>  jobs = new ArrayList<>();
    private final ReentrantLock   lock = new ReentrantLock(true); // fair

    // ── Metrics ───────────────────────────────────────────────────────────
    private volatile long totalEnqueued = 0;
    private volatile long totalDequeued = 0;
    private volatile long totalRejected = 0;

    // ── Constructors ──────────────────────────────────────────────────────

    /**
     * Creates a queue with the given capacity and scheduling algorithm.
     *
     * @param capacity  maximum number of queued jobs (≥ 1)
     * @param algorithm "FCFS", "SJF", or "HYBRID"
     */
    public PrintQueue(int capacity, String algorithm) {
        this(capacity, algorithm, DEFAULT_AGING_THRESHOLD_MS);
    }

    /**
     * Full constructor allowing a custom aging threshold.
     *
     * @param capacity         maximum queued jobs
     * @param algorithm        scheduling algorithm
     * @param agingThresholdMs ms after which HYBRID boosts a waiting job
     */
    public PrintQueue(int capacity, String algorithm, long agingThresholdMs) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be ≥ 1");
        this.capacity         = capacity;
        this.algorithm        = normalise(algorithm);
        this.agingThresholdMs = agingThresholdMs;
    }

    // ── Queue operations ──────────────────────────────────────────────────

    /**
     * Attempts to add a job to the queue.
     *
     * @param job the job to enqueue (must not be {@code null})
     * @return {@code true} if accepted; {@code false} if the queue is full
     */
    public boolean enqueue(PrintJob job) {
        if (job == null) throw new IllegalArgumentException("job must not be null");
        lock.lock();
        try {
            if (jobs.size() >= capacity) {
                totalRejected++;
                return false;
            }
            jobs.add(job);
            totalEnqueued++;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Selects and removes the next job according to the current algorithm.
     *
     * @param nowMs current simulation time (used for HYBRID aging)
     * @return the next {@link PrintJob}, or {@code null} if the queue is empty
     */
    public PrintJob dequeue(long nowMs) {
        lock.lock();
        try {
            if (jobs.isEmpty()) return null;
            PrintJob chosen = selectNext(nowMs);
            if (chosen != null) {
                jobs.remove(chosen);
                totalDequeued++;
            }
            return chosen;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the next candidate job without removing it.
     *
     * @param nowMs current simulation time
     * @return peek at the next job, or {@code null} if empty
     */
    public PrintJob peek(long nowMs) {
        lock.lock();
        try {
            return jobs.isEmpty() ? null : selectNext(nowMs);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes a specific job from the queue (for cancellation).
     *
     * @param jobId the job to cancel
     * @param nowMs current simulation time
     * @return {@code true} if the job was found and cancelled
     */
    public boolean cancel(long jobId, long nowMs) {
        lock.lock();
        try {
            for (PrintJob j : jobs) {
                if (j.getJobId() == jobId) {
                    jobs.remove(j);
                    j.markCancelled(nowMs);
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /** Drains all queued jobs and cancels each one. */
    public void clear(long nowMs) {
        lock.lock();
        try {
            for (PrintJob j : jobs) {
                j.markCancelled(nowMs);
            }
            jobs.clear();
        } finally {
            lock.unlock();
        }
    }

    // ── Algorithm selection ───────────────────────────────────────────────

    /**
     * Picks the best job according to the currently active algorithm.
     * Caller must hold {@code lock}.
     */
    private PrintJob selectNext(long nowMs) {
        return switch (algorithm) {
            case "SJF"    -> selectSjf();
            case "HYBRID" -> selectHybrid(nowMs);
            default       -> selectFcfs();   // "FCFS"
        };
    }

    /** FCFS: earliest-submitted job first. */
    private PrintJob selectFcfs() {
        return jobs.stream()
                   .min(Comparator.comparingLong(PrintJob::getSubmittedAt))
                   .orElse(null);
    }

    /** SJF: shortest (fewest estimated ms) job first. */
    private PrintJob selectSjf() {
        return jobs.stream()
                   .min(Comparator.comparingLong(j -> j.estimatedDurationMs(MS_PER_PAGE)))
                   .orElse(null);
    }

    /**
     * HYBRID: use SJF normally, but any job that has waited ≥
     * {@code agingThresholdMs} is promoted above all non-aged SJF candidates.
     * Among aged jobs, the one that waited longest wins (FCFS tie-break).
     */
    private PrintJob selectHybrid(long nowMs) {
        // Partition into aged and fresh buckets
        PrintJob longestAged = null;
        long     maxWait     = -1;
        PrintJob sjfFresh    = null;
        long     minDuration = Long.MAX_VALUE;

        for (PrintJob j : jobs) {
            long waited   = nowMs - j.getSubmittedAt();
            long duration = j.estimatedDurationMs(MS_PER_PAGE);

            if (waited >= agingThresholdMs) {
                // Aged: prefer the one that waited longest
                if (waited > maxWait) {
                    maxWait     = waited;
                    longestAged = j;
                }
            } else {
                // Fresh: prefer shortest
                if (duration < minDuration) {
                    minDuration = duration;
                    sjfFresh    = j;
                }
            }
        }
        // Aged jobs always win; fall back to SJF among fresh
        return longestAged != null ? longestAged : sjfFresh;
    }

    // ── Runtime configuration ─────────────────────────────────────────────

    /** Switches the scheduling algorithm at runtime. */
    public void setAlgorithm(String algorithm) {
        lock.lock();
        try {
            this.algorithm = normalise(algorithm);
        } finally {
            lock.unlock();
        }
    }

    /** Updates the aging threshold used by the HYBRID algorithm. */
    public void setAgingThresholdMs(long agingThresholdMs) {
        this.agingThresholdMs = agingThresholdMs;
    }

    // ── Query helpers ─────────────────────────────────────────────────────

    /** Returns an unmodifiable snapshot of all queued jobs (for display only). */
    public List<PrintJob> getQueuedJobsSnapshot() {
        lock.lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(jobs));
        } finally {
            lock.unlock();
        }
    }

    /** Current number of jobs in the queue. */
    public int size() {
        lock.lock();
        try {
            return jobs.size();
        } finally {
            lock.unlock();
        }
    }

    /** Returns {@code true} if the queue is empty. */
    public boolean isEmpty() {
        lock.lock();
        try {
            return jobs.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    /** Returns {@code true} if the queue has no free slot. */
    public boolean isFull() {
        lock.lock();
        try {
            return jobs.size() >= capacity;
        } finally {
            lock.unlock();
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public int    getCapacity()         { return capacity; }
    public String getAlgorithm()        { return algorithm; }
    public long   getAgingThresholdMs() { return agingThresholdMs; }
    public long   getTotalEnqueued()    { return totalEnqueued; }
    public long   getTotalDequeued()    { return totalDequeued; }
    public long   getTotalRejected()    { return totalRejected; }

    /** Resets all metrics (call during simulation reset). */
    public void resetMetrics() {
        lock.lock();
        try {
            totalEnqueued = 0;
            totalDequeued = 0;
            totalRejected = 0;
        } finally {
            lock.unlock();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static String normalise(String algo) {
        if (algo == null) return "FCFS";
        return switch (algo.toUpperCase()) {
            case "SJF"    -> "SJF";
            case "HYBRID" -> "HYBRID";
            default       -> "FCFS";
        };
    }

    @Override
    public String toString() {
        return String.format("PrintQueue{algo=%s, size=%d/%d, enqueued=%d, dequeued=%d, rejected=%d}",
            algorithm, jobs.size(), capacity, totalEnqueued, totalDequeued, totalRejected);
    }
}
