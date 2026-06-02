package com.printscheduler.model;

/**
 * Represents one physical printer in the simulation.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Track whether the printer is IDLE, BUSY, or in ERROR.</li>
 *   <li>Hold a reference to the {@link PrintJob} currently being processed.</li>
 *   <li>Accumulate per-printer metrics (jobs completed, total pages, total print time).</li>
 * </ul>
 *
 * <h3>Thread-safety</h3>
 * All mutable fields are {@code volatile}. The simulator's scheduling loop
 * is the only writer; the WebSocket broadcaster only reads.
 */
public class Printer {

    // ── Identity ──────────────────────────────────────────────────────────
    private final int    printerId;   // 1-based
    private final String name;        // e.g. "Printer-1"

    // ── State ─────────────────────────────────────────────────────────────
    private volatile PrinterStatus status        = PrinterStatus.IDLE;
    private volatile PrintJob      currentJob    = null;
    private volatile long          jobStartTime  = -1;  // sim-time ms
    private volatile long          jobFinishTime = -1;  // sim-time ms (expected)

    // ── Metrics ───────────────────────────────────────────────────────────
    private volatile int  jobsCompleted  = 0;
    private volatile long totalPages     = 0;
    private volatile long totalPrintTime = 0;  // ms of active printing

    // ── Constructor ───────────────────────────────────────────────────────

    /**
     * Creates a printer with the given 1-based ID.
     * Name is auto-generated as "Printer-{id}".
     */
    public Printer(int printerId) {
        this.printerId = printerId;
        this.name      = "Printer-" + printerId;
    }

    /** Creates a printer with a custom display name. */
    public Printer(int printerId, String name) {
        this.printerId = printerId;
        this.name      = name;
    }

    // ── Lifecycle helpers ─────────────────────────────────────────────────

    /**
     * Starts processing the given job.
     * The printer must be IDLE; call-site must enforce this.
     *
     * @param job          the job to process
     * @param nowMs        current simulation time
     * @param durationMs   how long (sim-time) the job will take
     */
    public void startJob(PrintJob job, long nowMs, long durationMs) {
        this.currentJob    = job;
        this.jobStartTime  = nowMs;
        this.jobFinishTime = nowMs + durationMs;
        this.status        = PrinterStatus.BUSY;
        job.markPrinting(this.printerId, nowMs);
    }

    /**
     * Finishes the current job successfully and returns it.
     * Printer transitions back to IDLE.
     *
     * @param nowMs current simulation time
     * @return the completed {@link PrintJob}
     */
    public PrintJob finishJob(long nowMs) {
        PrintJob finished = this.currentJob;
        if (finished != null) {
            finished.markCompleted(nowMs);
            this.jobsCompleted++;
            this.totalPages    += finished.getPageCount();
            this.totalPrintTime += (nowMs - jobStartTime);
        }
        this.currentJob    = null;
        this.jobStartTime  = -1;
        this.jobFinishTime = -1;
        this.status        = PrinterStatus.IDLE;
        return finished;
    }

    /**
     * Fails the current job and transitions the printer to ERROR state.
     *
     * @param nowMs current simulation time
     * @return the failed {@link PrintJob}, or {@code null} if none
     */
    public PrintJob failJob(long nowMs) {
        PrintJob failed = this.currentJob;
        if (failed != null) {
            failed.markFailed(nowMs);
        }
        this.currentJob    = null;
        this.jobStartTime  = -1;
        this.jobFinishTime = -1;
        this.status        = PrinterStatus.ERROR;
        return failed;
    }

    /** Clears an error and returns the printer to IDLE. */
    public void clearError() {
        this.status = PrinterStatus.IDLE;
    }

    /** Takes this printer offline (removed from the simulation pool). */
    public void goOffline() {
        this.status = PrinterStatus.OFFLINE;
    }

    /**
     * Returns {@code true} if the current job's expected finish time
     * has been reached or passed.
     *
     * @param nowMs current simulation time
     */
    public boolean isJobDue(long nowMs) {
        return status == PrinterStatus.BUSY && jobFinishTime >= 0 && nowMs >= jobFinishTime;
    }

    /** Resets all metrics and returns the printer to IDLE (used on simulation reset). */
    public void reset() {
        this.currentJob    = null;
        this.jobStartTime  = -1;
        this.jobFinishTime = -1;
        this.status        = PrinterStatus.IDLE;
        this.jobsCompleted = 0;
        this.totalPages    = 0;
        this.totalPrintTime = 0;
    }

    // ── Derived metrics ───────────────────────────────────────────────────

    /** Average pages per completed job. Returns 0 if no jobs done yet. */
    public double averagePagesPerJob() {
        return jobsCompleted == 0 ? 0.0 : (double) totalPages / jobsCompleted;
    }

    /** Average print duration per completed job in ms. Returns 0 if none. */
    public double averagePrintTimeMs() {
        return jobsCompleted == 0 ? 0.0 : (double) totalPrintTime / jobsCompleted;
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public int           getPrinterId()     { return printerId; }
    public String        getName()          { return name; }
    public PrinterStatus getStatus()        { return status; }
    public PrintJob      getCurrentJob()    { return currentJob; }
    public long          getJobStartTime()  { return jobStartTime; }
    public long          getJobFinishTime() { return jobFinishTime; }
    public int           getJobsCompleted() { return jobsCompleted; }
    public long          getTotalPages()    { return totalPages; }
    public long          getTotalPrintTime(){ return totalPrintTime; }

    @Override
    public String toString() {
        return String.format("Printer{id=%d, status=%s, jobsCompleted=%d}",
            printerId, status, jobsCompleted);
    }
}
