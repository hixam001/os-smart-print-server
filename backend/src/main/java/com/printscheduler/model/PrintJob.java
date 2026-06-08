package com.printscheduler.model;

import java.util.concurrent.atomic.AtomicLong;

public class PrintJob {

    private static final AtomicLong ID_SEQUENCE = new AtomicLong(0);

    public static void resetIdSequence() {
        ID_SEQUENCE.set(0);
    }

    private final long   jobId;
    private final int    userId;
    private final int    pageCount;
    private final boolean isColor;
    private final int    priority;
    private final long   submittedAt;

    private volatile JobStatus status        = JobStatus.QUEUED;
    private volatile int       assignedPrinterId = -1;
    private volatile long      startedAt     = -1;
    private volatile long      completedAt   = -1;

    public PrintJob(int userId, int pageCount, boolean isColor,
                    int priority, long submittedAt) {
        this.jobId       = ID_SEQUENCE.incrementAndGet();
        this.userId      = userId;
        this.pageCount   = pageCount;
        this.isColor     = isColor;
        this.priority    = priority;
        this.submittedAt = submittedAt;
    }

    public long getWaitingTimeMs() {
        return startedAt < 0 ? -1 : startedAt - submittedAt;
    }

    public long getTurnaroundTimeMs() {
        return completedAt < 0 ? -1 : completedAt - submittedAt;
    }

    public long estimatedDurationMs(long msPerPage) {
        return pageCount * msPerPage * (isColor ? 2 : 1);
    }

    public void markPrinting(int printerId, long nowMs) {
        this.assignedPrinterId = printerId;
        this.startedAt         = nowMs;
        this.status            = JobStatus.PRINTING;
    }

    public void markCompleted(long nowMs) {
        this.completedAt = nowMs;
        this.status      = JobStatus.COMPLETED;
    }

    public void markFailed(long nowMs) {
        this.completedAt = nowMs;
        this.status      = JobStatus.FAILED;
    }

    public void markCancelled(long nowMs) {
        this.completedAt = nowMs;
        this.status      = JobStatus.CANCELLED;
    }

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
