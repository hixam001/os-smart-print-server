package com.printscheduler.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A point-in-time snapshot of the entire simulation.
 *
 * <p>This class is what the {@code SimulationServiceImpl} returns from
 * {@code getState()} and what the WebSocket broadcaster serialises
 * into JSON every 100 ms.  It contains everything the frontend needs
 * to render the live dashboard:
 *
 * <ul>
 *   <li>Overall status + elapsed time</li>
 *   <li>Active scheduling algorithm and simulation speed</li>
 *   <li>Queue depth and per-algorithm admission metrics</li>
 *   <li>Per-printer state (status, current job, completions)</li>
 *   <li>All QUEUED jobs currently waiting</li>
 *   <li>Aggregated simulation-wide metrics</li>
 * </ul>
 *
 * <p>All fields are plain POJOs so Jackson can serialise without any
 * additional annotations.  No live mutable state is exposed—callers
 * should build a new instance on each tick.
 */
public class SimulationState {

    // ── Top-level simulation context ──────────────────────────────────────
    private SimulationStatus status;
    private long             elapsedMs;
    private String           algorithm;
    private double           simulationSpeed;
    private int              tick;              // simulation tick counter

    // ── Queue summary ──────────────────────────────────────────────────────
    private int  queueSize;
    private int  queueCapacity;
    private long totalEnqueued;
    private long totalDequeued;
    private long totalRejected;   // jobs turned away (queue full)

    // ── Per-printer snapshots ─────────────────────────────────────────────
    private List<PrinterInfo> printers = new ArrayList<>();

    // ── Queued job list (for the live queue panel) ─────────────────────────
    private List<JobInfo> queuedJobs = new ArrayList<>();

    // ── Aggregated metrics ────────────────────────────────────────────────
    private Metrics metrics = new Metrics();

    // ── Constructors ──────────────────────────────────────────────────────
    public SimulationState() {}

    // =========================================================================
    //  Nested: PrinterInfo
    // =========================================================================

    /**
     * Lightweight projection of a {@link Printer} for JSON serialisation.
     * Avoids exposing the mutable Printer domain object directly.
     */
    public static class PrinterInfo {
        private int    printerId;
        private String name;
        private String status;              // PrinterStatus name
        private Long   currentJobId;        // null if idle
        private int    currentJobPages;     // 0 if idle
        private long   jobProgressPercent;  // 0–100
        private int    jobsCompleted;
        private long   totalPages;
        private double averagePrintTimeMs;

        public PrinterInfo() {}

        /**
         * Builds a {@code PrinterInfo} from a live {@link Printer} and
         * the current simulation time.
         */
        public static PrinterInfo from(Printer printer, long nowMs) {
            PrinterInfo pi = new PrinterInfo();
            pi.printerId         = printer.getPrinterId();
            pi.name              = printer.getName();
            pi.status            = printer.getStatus().name();
            pi.jobsCompleted     = printer.getJobsCompleted();
            pi.totalPages        = printer.getTotalPages();
            pi.averagePrintTimeMs = printer.averagePrintTimeMs();

            PrintJob cur = printer.getCurrentJob();
            if (cur != null) {
                pi.currentJobId    = cur.getJobId();
                pi.currentJobPages = cur.getPageCount();

                long start    = printer.getJobStartTime();
                long finish   = printer.getJobFinishTime();
                long total    = finish - start;
                long elapsed  = nowMs - start;
                pi.jobProgressPercent = total > 0
                    ? Math.min(100, elapsed * 100 / total)
                    : 0;
            } else {
                pi.currentJobId       = null;
                pi.currentJobPages    = 0;
                pi.jobProgressPercent = 0;
            }
            return pi;
        }

        // Getters
        public int    getPrinterId()          { return printerId; }
        public String getName()               { return name; }
        public String getStatus()             { return status; }
        public Long   getCurrentJobId()       { return currentJobId; }
        public int    getCurrentJobPages()    { return currentJobPages; }
        public long   getJobProgressPercent() { return jobProgressPercent; }
        public int    getJobsCompleted()      { return jobsCompleted; }
        public long   getTotalPages()         { return totalPages; }
        public double getAveragePrintTimeMs() { return averagePrintTimeMs; }

        // Setters (for Jackson / tests)
        public void setPrinterId(int v)          { this.printerId = v; }
        public void setName(String v)             { this.name = v; }
        public void setStatus(String v)           { this.status = v; }
        public void setCurrentJobId(Long v)       { this.currentJobId = v; }
        public void setCurrentJobPages(int v)     { this.currentJobPages = v; }
        public void setJobProgressPercent(long v) { this.jobProgressPercent = v; }
        public void setJobsCompleted(int v)       { this.jobsCompleted = v; }
        public void setTotalPages(long v)         { this.totalPages = v; }
        public void setAveragePrintTimeMs(double v){ this.averagePrintTimeMs = v; }
    }

    // =========================================================================
    //  Nested: JobInfo
    // =========================================================================

    /**
     * Lightweight projection of a {@link PrintJob} for JSON serialisation.
     */
    public static class JobInfo {
        private long   jobId;
        private int    userId;
        private int    pageCount;
        private boolean color;
        private int    priority;
        private String status;
        private long   submittedAt;
        private long   waitingTimeMs;   // -1 if still queued
        private long   turnaroundTimeMs; // -1 if not completed

        public JobInfo() {}

        /** Builds a {@code JobInfo} from a live {@link PrintJob}. */
        public static JobInfo from(PrintJob job) {
            JobInfo ji = new JobInfo();
            ji.jobId          = job.getJobId();
            ji.userId         = job.getUserId();
            ji.pageCount      = job.getPageCount();
            ji.color          = job.isColor();
            ji.priority       = job.getPriority();
            ji.status         = job.getStatus().name();
            ji.submittedAt    = job.getSubmittedAt();
            ji.waitingTimeMs  = job.getWaitingTimeMs();
            ji.turnaroundTimeMs = job.getTurnaroundTimeMs();
            return ji;
        }

        // Getters
        public long   getJobId()           { return jobId; }
        public int    getUserId()          { return userId; }
        public int    getPageCount()       { return pageCount; }
        public boolean isColor()           { return color; }
        public int    getPriority()        { return priority; }
        public String getStatus()          { return status; }
        public long   getSubmittedAt()     { return submittedAt; }
        public long   getWaitingTimeMs()   { return waitingTimeMs; }
        public long   getTurnaroundTimeMs(){ return turnaroundTimeMs; }

        // Setters (for Jackson / tests)
        public void setJobId(long v)            { this.jobId = v; }
        public void setUserId(int v)            { this.userId = v; }
        public void setPageCount(int v)         { this.pageCount = v; }
        public void setColor(boolean v)         { this.color = v; }
        public void setPriority(int v)          { this.priority = v; }
        public void setStatus(String v)         { this.status = v; }
        public void setSubmittedAt(long v)      { this.submittedAt = v; }
        public void setWaitingTimeMs(long v)    { this.waitingTimeMs = v; }
        public void setTurnaroundTimeMs(long v) { this.turnaroundTimeMs = v; }
    }

    // =========================================================================
    //  Nested: Metrics
    // =========================================================================

    /**
     * Aggregated performance metrics for the simulation run so far.
     *
     * <p>All values are computed from the {@link Database} at snapshot time.
     */
    public static class Metrics {
        private int    totalJobsCompleted;
        private int    totalJobsFailed;
        private int    totalJobsCancelled;
        private double avgWaitingTimeMs;
        private double avgTurnaroundTimeMs;
        private double avgPageCount;
        private long   throughputJobsPerMin;  // jobs completed per simulated minute
        private double colorJobRatio;         // fraction of completed jobs that were colour

        public Metrics() {}

        // Getters
        public int    getTotalJobsCompleted()   { return totalJobsCompleted; }
        public int    getTotalJobsFailed()      { return totalJobsFailed; }
        public int    getTotalJobsCancelled()   { return totalJobsCancelled; }
        public double getAvgWaitingTimeMs()     { return avgWaitingTimeMs; }
        public double getAvgTurnaroundTimeMs()  { return avgTurnaroundTimeMs; }
        public double getAvgPageCount()         { return avgPageCount; }
        public long   getThroughputJobsPerMin() { return throughputJobsPerMin; }
        public double getColorJobRatio()        { return colorJobRatio; }

        // Setters
        public void setTotalJobsCompleted(int v)    { this.totalJobsCompleted = v; }
        public void setTotalJobsFailed(int v)       { this.totalJobsFailed = v; }
        public void setTotalJobsCancelled(int v)    { this.totalJobsCancelled = v; }
        public void setAvgWaitingTimeMs(double v)   { this.avgWaitingTimeMs = v; }
        public void setAvgTurnaroundTimeMs(double v){ this.avgTurnaroundTimeMs = v; }
        public void setAvgPageCount(double v)       { this.avgPageCount = v; }
        public void setThroughputJobsPerMin(long v) { this.throughputJobsPerMin = v; }
        public void setColorJobRatio(double v)      { this.colorJobRatio = v; }
    }

    // ── Top-level getters ─────────────────────────────────────────────────

    public SimulationStatus getStatus()          { return status; }
    public long             getElapsedMs()        { return elapsedMs; }
    public String           getAlgorithm()        { return algorithm; }
    public double           getSimulationSpeed()  { return simulationSpeed; }
    public int              getTick()             { return tick; }
    public int              getQueueSize()        { return queueSize; }
    public int              getQueueCapacity()    { return queueCapacity; }
    public long             getTotalEnqueued()    { return totalEnqueued; }
    public long             getTotalDequeued()    { return totalDequeued; }
    public long             getTotalRejected()    { return totalRejected; }
    public List<PrinterInfo> getPrinters()       { return Collections.unmodifiableList(printers); }
    public List<JobInfo>     getQueuedJobs()     { return Collections.unmodifiableList(queuedJobs); }
    public Metrics           getMetrics()        { return metrics; }

    // ── Top-level setters ─────────────────────────────────────────────────

    public void setStatus(SimulationStatus v)     { this.status = v; }
    public void setElapsedMs(long v)              { this.elapsedMs = v; }
    public void setAlgorithm(String v)            { this.algorithm = v; }
    public void setSimulationSpeed(double v)      { this.simulationSpeed = v; }
    public void setTick(int v)                    { this.tick = v; }
    public void setQueueSize(int v)               { this.queueSize = v; }
    public void setQueueCapacity(int v)           { this.queueCapacity = v; }
    public void setTotalEnqueued(long v)          { this.totalEnqueued = v; }
    public void setTotalDequeued(long v)          { this.totalDequeued = v; }
    public void setTotalRejected(long v)          { this.totalRejected = v; }
    public void setPrinters(List<PrinterInfo> v)  { this.printers = v; }
    public void setQueuedJobs(List<JobInfo> v)    { this.queuedJobs = v; }
    public void setMetrics(Metrics v)             { this.metrics = v; }
}
