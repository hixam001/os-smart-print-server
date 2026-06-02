package com.printscheduler.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * In-memory job history store and metrics aggregator.
 *
 * <p>The {@code Database} accumulates every {@link PrintJob} whose lifecycle
 * has ended (COMPLETED, FAILED, or CANCELLED) so that:
 * <ul>
 *   <li>The REST export endpoint can stream all historical records.</li>
 *   <li>The {@code SimulationState.Metrics} block can be recomputed cheaply.</li>
 *   <li>Per-user and per-printer breakdowns are available on demand.</li>
 * </ul>
 *
 * <h3>Thread-safety</h3>
 * The internal store uses a {@link CopyOnWriteArrayList}: writes (job
 * completions) are infrequent, reads (snapshot, export) are frequent.
 * Per-user index uses a {@link ConcurrentHashMap}.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Call {@link #record(PrintJob)} when a job reaches a terminal state.</li>
 *   <li>Call {@link #buildMetrics(long)} to get a fresh {@link SimulationState.Metrics}.</li>
 *   <li>Call {@link #reset()} when the simulation resets.</li>
 * </ol>
 */
public class Database {

    // ── Storage ───────────────────────────────────────────────────────────

    /** All terminal jobs in insertion order. Thread-safe for concurrent reads. */
    private final CopyOnWriteArrayList<PrintJob> allJobs = new CopyOnWriteArrayList<>();

    /**
     * Per-user index for user-breakdown queries.
     * Key = userId, Value = list of that user's terminal jobs.
     */
    private final ConcurrentHashMap<Integer, CopyOnWriteArrayList<PrintJob>> byUser =
        new ConcurrentHashMap<>();

    // ── Write API ─────────────────────────────────────────────────────────

    /**
     * Records a job that has reached a terminal state.
     * Jobs in QUEUED or PRINTING state are silently ignored.
     *
     * @param job the finished, failed, or cancelled job
     */
    public void record(PrintJob job) {
        if (job == null) return;
        JobStatus s = job.getStatus();
        if (s != JobStatus.COMPLETED && s != JobStatus.FAILED && s != JobStatus.CANCELLED) {
            return;  // only terminal states
        }
        allJobs.add(job);
        byUser.computeIfAbsent(job.getUserId(), id -> new CopyOnWriteArrayList<>())
              .add(job);
    }

    // ── Reset ─────────────────────────────────────────────────────────────

    /** Clears all stored jobs. Call this during a simulation reset. */
    public void reset() {
        allJobs.clear();
        byUser.clear();
    }

    // ── Read API ──────────────────────────────────────────────────────────

    /**
     * Returns an unmodifiable view of all terminal jobs (insertion order).
     * Safe for streaming to the export endpoint.
     */
    public List<PrintJob> getAllJobs() {
        return Collections.unmodifiableList(new ArrayList<>(allJobs));
    }

    /**
     * Returns all terminal jobs for the given user, or an empty list if none.
     */
    public List<PrintJob> getJobsByUser(int userId) {
        CopyOnWriteArrayList<PrintJob> userJobs = byUser.get(userId);
        return userJobs == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(userJobs));
    }

    /** Returns only completed (COMPLETED status) jobs. */
    public List<PrintJob> getCompletedJobs() {
        return allJobs.stream()
                      .filter(j -> j.getStatus() == JobStatus.COMPLETED)
                      .collect(Collectors.toList());
    }

    /** Returns only failed (FAILED status) jobs. */
    public List<PrintJob> getFailedJobs() {
        return allJobs.stream()
                      .filter(j -> j.getStatus() == JobStatus.FAILED)
                      .collect(Collectors.toList());
    }

    /** Returns only cancelled jobs. */
    public List<PrintJob> getCancelledJobs() {
        return allJobs.stream()
                      .filter(j -> j.getStatus() == JobStatus.CANCELLED)
                      .collect(Collectors.toList());
    }

    /** Total number of stored terminal jobs. */
    public int totalJobCount() {
        return allJobs.size();
    }

    // ── Metrics computation ───────────────────────────────────────────────

    /**
     * Computes a fresh {@link SimulationState.Metrics} from all stored jobs.
     *
     * @param elapsedMs total simulation run time so far (for throughput calc)
     * @return up-to-date metrics snapshot; never {@code null}
     */
    public SimulationState.Metrics buildMetrics(long elapsedMs) {
        SimulationState.Metrics m = new SimulationState.Metrics();

        List<PrintJob> completed = getCompletedJobs();
        List<PrintJob> failed    = getFailedJobs();
        List<PrintJob> cancelled = getCancelledJobs();

        m.setTotalJobsCompleted(completed.size());
        m.setTotalJobsFailed(failed.size());
        m.setTotalJobsCancelled(cancelled.size());

        if (!completed.isEmpty()) {
            OptionalDouble avgWait = completed.stream()
                .mapToLong(PrintJob::getWaitingTimeMs)
                .filter(v -> v >= 0)
                .average();
            m.setAvgWaitingTimeMs(avgWait.orElse(0.0));

            OptionalDouble avgTat = completed.stream()
                .mapToLong(PrintJob::getTurnaroundTimeMs)
                .filter(v -> v >= 0)
                .average();
            m.setAvgTurnaroundTimeMs(avgTat.orElse(0.0));

            OptionalDouble avgPages = completed.stream()
                .mapToInt(PrintJob::getPageCount)
                .average();
            m.setAvgPageCount(avgPages.orElse(0.0));

            long colorCount = completed.stream().filter(PrintJob::isColor).count();
            m.setColorJobRatio(completed.isEmpty() ? 0.0
                : (double) colorCount / completed.size());

            // Throughput: jobs per simulated minute
            long elapsedMin = elapsedMs / 60_000;
            m.setThroughputJobsPerMin(elapsedMin > 0
                ? completed.size() / elapsedMin
                : completed.size());   // less than one minute: raw count
        }

        return m;
    }

    /**
     * Returns a per-user breakdown map.
     * Key = userId, Value = count of that user's completed jobs.
     */
    public Map<Integer, Long> completedJobsPerUser() {
        return byUser.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().stream()
                      .filter(j -> j.getStatus() == JobStatus.COMPLETED)
                      .count()
            ));
    }

    /**
     * Generates a CSV string of all completed jobs.
     * Suitable for the {@code /api/simulation/export?format=csv&type=jobs} endpoint.
     */
    public String toCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("jobId,userId,pageCount,color,priority,status,")
          .append("submittedAt,waitingTimeMs,turnaroundTimeMs\n");

        for (PrintJob j : allJobs) {
            sb.append(j.getJobId()).append(',')
              .append(j.getUserId()).append(',')
              .append(j.getPageCount()).append(',')
              .append(j.isColor()).append(',')
              .append(j.getPriority()).append(',')
              .append(j.getStatus().name()).append(',')
              .append(j.getSubmittedAt()).append(',')
              .append(j.getWaitingTimeMs()).append(',')
              .append(j.getTurnaroundTimeMs()).append('\n');
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("Database{total=%d, completed=%d, failed=%d, cancelled=%d}",
            allJobs.size(), getCompletedJobs().size(),
            getFailedJobs().size(), getCancelledJobs().size());
    }
}
