package com.printscheduler.model;

public class Printer {

    private final int    printerId;
    private final String name;

    private volatile PrinterStatus status        = PrinterStatus.IDLE;
    private volatile PrintJob      currentJob    = null;
    private volatile long          jobStartTime  = -1;
    private volatile long          jobFinishTime = -1;

    private volatile int  jobsCompleted  = 0;
    private volatile long totalPages     = 0;
    private volatile long totalPrintTime = 0;

    public Printer(int printerId) {
        this.printerId = printerId;
        this.name      = "Printer-" + printerId;
    }

    public Printer(int printerId, String name) {
        this.printerId = printerId;
        this.name      = name;
    }

    public void startJob(PrintJob job, long nowMs, long durationMs) {
        this.currentJob    = job;
        this.jobStartTime  = nowMs;
        this.jobFinishTime = nowMs + durationMs;
        this.status        = PrinterStatus.BUSY;
        job.markPrinting(this.printerId, nowMs);
    }

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

    public void clearError() {
        this.status = PrinterStatus.IDLE;
    }

    public void goOffline() {
        this.status = PrinterStatus.OFFLINE;
    }

    public boolean isJobDue(long nowMs) {
        return status == PrinterStatus.BUSY && jobFinishTime >= 0 && nowMs >= jobFinishTime;
    }

    public void reset() {
        this.currentJob    = null;
        this.jobStartTime  = -1;
        this.jobFinishTime = -1;
        this.status        = PrinterStatus.IDLE;
        this.jobsCompleted = 0;
        this.totalPages    = 0;
        this.totalPrintTime = 0;
    }

    public double averagePagesPerJob() {
        return jobsCompleted == 0 ? 0.0 : (double) totalPages / jobsCompleted;
    }

    public double averagePrintTimeMs() {
        return jobsCompleted == 0 ? 0.0 : (double) totalPrintTime / jobsCompleted;
    }

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
