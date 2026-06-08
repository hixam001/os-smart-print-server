package com.printscheduler.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class PrintQueue {

    public static final long DEFAULT_AGING_THRESHOLD_MS = 30_000L;

    public static final long MS_PER_PAGE = 3_000L;

    public static final long MS_COMPUTE_DELAY = 2_500L;

    private final int    capacity;
    private       String algorithm;
    private       long   agingThresholdMs;

    private final List<PrintJob>  jobs = new ArrayList<>();
    private final ReentrantLock   lock = new ReentrantLock(true);

    private volatile long totalEnqueued = 0;
    private volatile long totalDequeued = 0;
    private volatile long totalRejected = 0;

    public PrintQueue(int capacity, String algorithm) {
        this(capacity, algorithm, DEFAULT_AGING_THRESHOLD_MS);
    }

    public PrintQueue(int capacity, String algorithm, long agingThresholdMs) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be ≥ 1");
        this.capacity         = capacity;
        this.algorithm        = normalise(algorithm);
        this.agingThresholdMs = agingThresholdMs;
    }

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

    public PrintJob peek(long nowMs) {
        lock.lock();
        try {
            return jobs.isEmpty() ? null : selectNext(nowMs);
        } finally {
            lock.unlock();
        }
    }

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

    private PrintJob selectNext(long nowMs) {
        return switch (algorithm) {
            case "SJF"    -> selectSjf();
            case "HYBRID" -> selectHybrid(nowMs);
            default       -> selectFcfs();
        };
    }

    private PrintJob selectFcfs() {
        return jobs.stream()
                   .min(Comparator.comparingLong(PrintJob::getSubmittedAt))
                   .orElse(null);
    }

    private PrintJob selectSjf() {
        return jobs.stream()
                   .min(Comparator.comparingLong(j -> j.estimatedDurationMs(MS_PER_PAGE)))
                   .orElse(null);
    }

    private PrintJob selectHybrid(long nowMs) {

        PrintJob longestAged = null;
        long     maxWait     = -1;
        PrintJob sjfFresh    = null;
        long     minDuration = Long.MAX_VALUE;

        for (PrintJob j : jobs) {
            long waited   = nowMs - j.getSubmittedAt();
            long duration = j.estimatedDurationMs(MS_PER_PAGE);

            if (waited >= agingThresholdMs) {

                if (waited > maxWait) {
                    maxWait     = waited;
                    longestAged = j;
                }
            } else {

                if (duration < minDuration) {
                    minDuration = duration;
                    sjfFresh    = j;
                }
            }
        }

        return longestAged != null ? longestAged : sjfFresh;
    }

    public void setAlgorithm(String algorithm) {
        lock.lock();
        try {
            this.algorithm = normalise(algorithm);
        } finally {
            lock.unlock();
        }
    }

    public void setAgingThresholdMs(long agingThresholdMs) {
        this.agingThresholdMs = agingThresholdMs;
    }

    public List<PrintJob> getQueuedJobsSnapshot() {
        lock.lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(jobs));
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return jobs.size();
        } finally {
            lock.unlock();
        }
    }

    public boolean isEmpty() {
        lock.lock();
        try {
            return jobs.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    public boolean isFull() {
        lock.lock();
        try {
            return jobs.size() >= capacity;
        } finally {
            lock.unlock();
        }
    }

    public int    getCapacity()         { return capacity; }
    public String getAlgorithm()        { return algorithm; }
    public long   getAgingThresholdMs() { return agingThresholdMs; }
    public long   getTotalEnqueued()    { return totalEnqueued; }
    public long   getTotalDequeued()    { return totalDequeued; }
    public long   getTotalRejected()    { return totalRejected; }

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
