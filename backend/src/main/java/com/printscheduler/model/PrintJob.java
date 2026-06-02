package com.printscheduler.model;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a single print request submitted by a user.
 *
 * <h3>Immutable identity fields (set at construction)</h3>
 * <ul>
 *   <li>{@code jobId}    – unique monotonically increasing ID</li>
 *   <li>{@code userId}   – ID of the submitting user (1-based)</li>
 *   <li>{@code pageCount}– number of pages to print</li>
 *   <li>{@code isColor}  – colour vs. monochrome job</li>
 *   <li>{@code priority} – higher value = higher scheduling priority</li>
 *   <li>{@code submittedAt} – simulation-time tick when the job was queued</li>
 * </ul>
 *
 * <h3>Mutable lifecycle fields</h3>
 * <ul>
 *   <li>{@code status}      – current {@link JobStatus}</li>
 *   <li>{@code assignedPrinterId} – which printer is processing this job</li>
 *   <li>{@code startedAt}   – tick when printing began</li>
 *   <li>{@code completedAt} – tick when printing ended (or job failed/cancelled)</li>
 * </ul>
 *
 * <h3>Thread-safety</h3>
 * Mutable fields are written only by the single scheduler/printer thread.
 * The {@code volatile} qualifier on {@code status} ensures that reading threads
 * (e.g. the WebSocket broadcaster) always see the latest value.
 */
public class PrintJob {

    // ── ID generator ─────────────────────────────────────────────────────
    private static final AtomicLong ID_SEQUENCE = new AtomicLong(0);

    /** Resets the global ID counter. Call this during simulation reset. */
    public static void resetIdSequence() {
        ID_SEQUENCE.set(0);
    }

    // ── Identity (immutable after construction) ───────────────────────────
    private final long   jobId;
    private final int    userId;
    private final int    pageCount;
    private final boolean isColor;
    private final int    priority;      // higher = more urgent
    private final long   submittedAt;   // simulation tick (ms)

    // ── Lifecycle (mutable) ───────────────────────────────────────────────
    private volatile JobStatus status        = JobStatus.QUEUED;
    private volatile int       assignedPrinterId = -1;  // -1 = unassigned
    private volatile long      startedAt     = -1;
    private volatile long      completedAt   = -1;

    // ── Constructor ───────────────────────────────────────────────────────

    /**
     * Creates a new print job and assigns it the next global job ID.
     *
     * @param userId      1-based user ID of the submitter
     * @param pageCount   number of pages (≥ 1)
     * @param isColor     {@code true} for colour printing
     * @param priority    scheduling priority (higher = sooner)
     * @param submittedAt simulation-time milliseconds when the job was queued
     */
    public PrintJob(int userId, int pageCount, boolean isColor,
                    int priority, long submittedAt) {
        this.jobId       = ID_SEQUENCE.incrementAndGet();
        this.userId      = userId;
        this.pageCount   = pageCount;
        this.isColor     = isColor;
        this.priority    = priority;
        this.submittedAt = submittedAt;
    }

    // ── Computed metrics ──────────────────────────────────────────────────

    /**
     * Waiting time = time from submission until printing starts.
     *
     * @return waiting time in ms, or -1 if printing has not started yet
     */
    public long getWaitingTimeMs() {
        return startedAt < 0 ? -1 : startedAt - submittedAt;
    }

    /**
     * Turnaround time = time from submission until completion/failure/cancellation.
     *
     * @return turnaround time in ms, or -1 if the job is not yet finished
     */
    public long getTurnaroundTimeMs() {
        return completedAt < 0 ? -1 : completedAt - submittedAt;
    }

    /**
     * Estimated print duration based on page count and colour mode.
     * Used by the SJF scheduler as the "burst time" estimate.
     * Colour pages take ~2× as long as monochrome pages.
     *
     * @param msPerPage milliseconds per page for monochrome
     * @return estimated duration in ms
     */
    public long estimatedDurationMs(long msPerPage) {
        return pageCount * msPerPage * (isColor ? 2 : 1);
    }

    // ── Lifecycle helpers ─────────────────────────────────────────────────

    /** Marks this job as currently being printed by the given printer. */
    public void markPrinting(int printerId, long nowMs) {
        this.assignedPrinterId = printerId;
        this.startedAt         = nowMs;
        this.status            = JobStatus.PRINTING;
    }

    /** Marks this job as successfully completed. */
    public void markCompleted(long nowMs) {
        this.completedAt = nowMs;
        this.status      = JobStatus.COMPLETED;
    }

    /** Marks this job as failed. */
    public void markFailed(long nowMs) {
        this.completedAt = nowMs;
        this.status      = JobStatus.FAILED;
    }

    /** Removes this job from the queue (before printing began). */
    public void markCancelled(long nowMs) {
        this.completedAt = nowMs;
        this.status      = JobStatus.CANCELLED;
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public long      getJobId()             { return jobId; }
    public int       getUserId()            { return userId; }
    public int       getPageCount()         { return pageCount; }
    public boolean   isColor()              { return isColor; }
    public int       getPriority()          { return priority; }
    public long      getSubmittedAt()       { return submittedAt; }
    public JobStatus getStatus()            { return status; }
    public int       getAssignedPrinterId() { return assignedPrinterId; }
    public long      getStartedAt()         { return startedAt; }
    public long      getCompletedAt()       { return completedAt; }

    // ── Status setters (for direct manipulation in tests / reset) ─────────
    public void setStatus(JobStatus status)            { this.status = status; }
    public void setAssignedPrinterId(int printerId)    { this.assignedPrinterId = printerId; }
    public void setStartedAt(long startedAt)           { this.startedAt = startedAt; }
    public void setCompletedAt(long completedAt)       { this.completedAt = completedAt; }

    @Override
    public String toString() {
        return String.format("PrintJob{id=%d, user=%d, pages=%d, color=%b, priority=%d, status=%s}",
            jobId, userId, pageCount, isColor, priority, status);
    }
}
