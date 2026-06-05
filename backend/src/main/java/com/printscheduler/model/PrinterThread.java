package com.printscheduler.model;

import com.printscheduler.service.PrintServerSimulator;
import java.util.Map;
import java.util.Random;

/**
 * Consumer thread — represents one physical printer.
 *
 * <h3>OS Concepts demonstrated:</h3>
 * <ul>
 *   <li><b>Producer-Consumer</b>: blocks on {@code semaphore.acquire()} when queue is empty</li>
 *   <li><b>Mutual Exclusion</b>: queue dequeue uses a ReentrantLock (fair=true)</li>
 *   <li><b>Real Print Delay</b>: sleeps {@code MS_PER_PAGE} per page, yielding CPU between pages</li>
 *   <li><b>Hardware Failure</b>: 5 % random paper-jam / hardware error scenario</li>
 * </ul>
 */
public class PrinterThread extends Thread {

    private static final long WARMUP_MS = 500L;  // simulated printer warm-up delay

    private final Printer printer;
    private final PrintServerSimulator simulator;
    private final Random random = new Random();

    public PrinterThread(Printer printer, PrintServerSimulator simulator) {
        super("Printer-" + printer.getPrinterId());
        this.printer   = printer;
        this.simulator = simulator;
    }

    @Override
    public void run() {
        try {
            // Printer warm-up delay — simulates device ready signal
            simulator.getClock().sleepSimulated(WARMUP_MS, simulator::isRunning,
                    simulator.getPauseCoordinator());

            while (simulator.isRunning()) {
                simulator.getPauseCoordinator().checkPause();

                // ── SEMAPHORE ACQUIRE ─────────────────────────────────────────
                // Blocks here when the queue is empty (permits == 0).
                // This is the core of the Producer-Consumer pattern:
                // the printer thread waits until a user thread does release().
                simulator.getJobsAvailableSemaphore().acquire();

                if (!simulator.isRunning()) break;
                simulator.getPauseCoordinator().checkPause();

                // ── MUTEX (LOCK) — dequeue under ReentrantLock ────────────────
                long nowMs = simulator.getClock().getSimulatedTimeMs();
                PrintJob job = simulator.getQueue().dequeue(nowMs);
                if (job == null) continue;   // spurious wakeup / queue cleared

                // ── PRINT START ───────────────────────────────────────────────
                long durationMs = job.estimatedDurationMs(PrintQueue.MS_PER_PAGE);
                printer.startJob(job, nowMs, durationMs);

                simulator.publishEvent("PRINT_START", Map.of(
                    "printerId",    printer.getPrinterId(),
                    "printerName",  printer.getName(),
                    "jobId",        job.getJobId(),
                    "userId",       job.getUserId(),
                    "pageCount",    job.getPageCount(),
                    "color",        job.isColor(),
                    "priority",     job.getPriority(),
                    "waitingTimeMs", Math.max(0, nowMs - job.getSubmittedAt()),
                    "startedAt",    nowMs
                ));

                try {
                    // ── COMPUTE / RENDER DELAY ────────────────────────────────
                    // Real printers spend 2-5s receiving & rendering the document
                    // (rasterizing fonts, processing images) before page 1 exits.
                    simulator.publishEvent("PRINTER_COMPUTING", Map.of(
                        "printerId",   printer.getPrinterId(),
                        "printerName", printer.getName(),
                        "jobId",       job.getJobId(),
                        "computeMs",   PrintQueue.MS_COMPUTE_DELAY
                    ));
                    simulator.getClock().sleepSimulated(
                        PrintQueue.MS_COMPUTE_DELAY,
                        simulator::isRunning,
                        simulator.getPauseCoordinator()
                    );
                    // ── PAGE-BY-PAGE PRINTING DELAY ───────────────────────────
                    // Each page takes MS_PER_PAGE simulated ms so the progress
                    // bar in the UI visually advances page by page.
                    boolean failed = false;
                    for (int page = 1; page <= job.getPageCount(); page++) {
                        if (!simulator.isRunning()) throw new InterruptedException();
                        simulator.getPauseCoordinator().checkPause();

                        // Simulate printing one page
                        simulator.getClock().sleepSimulated(
                            PrintQueue.MS_PER_PAGE,
                            simulator::isRunning,
                            simulator.getPauseCoordinator()
                        );

                        // Brief paper-feed delay between pages (roller advance)
                        if (page < job.getPageCount()) {
                            simulator.getClock().sleepSimulated(
                                300L,
                                simulator::isRunning,
                                simulator.getPauseCoordinator()
                            );
                        }

                        // 5% chance of hardware error on any page (paper jam, etc.)
                        if (random.nextDouble() < 0.05) {
                            long failTime = simulator.getClock().getSimulatedTimeMs();
                            PrintJob failedJob = printer.failJob(failTime);

                            if (failedJob != null) {
                                simulator.getDatabase().record(failedJob);
                                simulator.publishEvent("PRINT_FAIL", Map.of(
                                    "printerId",   printer.getPrinterId(),
                                    "printerName", printer.getName(),
                                    "jobId",       failedJob.getJobId(),
                                    "pageCount",   job.getPageCount(),
                                    "pageFailed",  page,
                                    "reason",      "Hardware error / paper jam on page " + page,
                                    "failedAt",    failTime
                                ));
                            }

                            // Printer repair delay (e.g., clear jam, reset heads)
                            simulator.publishEvent("PRINTER_REPAIRING", Map.of(
                                "printerId",   printer.getPrinterId(),
                                "printerName", printer.getName(),
                                "repairMs",    5000
                            ));
                            simulator.getClock().sleepSimulated(5000L, simulator::isRunning,
                                    simulator.getPauseCoordinator());
                            printer.clearError();
                            simulator.publishEvent("PRINTER_RECOVER", Map.of(
                                "printerId",   printer.getPrinterId(),
                                "printerName", printer.getName(),
                                "status",      "IDLE"
                            ));
                            failed = true;
                            break;
                        }
                    }

                    if (!failed) {
                        // ── JOB COMPLETE ──────────────────────────────────────
                        long finishTime = simulator.getClock().getSimulatedTimeMs();
                        PrintJob done = printer.finishJob(finishTime);
                        if (done != null) {
                            simulator.getDatabase().record(done);
                            simulator.publishEvent("PRINT_COMPLETED", Map.of(
                                "printerId",        printer.getPrinterId(),
                                "printerName",      printer.getName(),
                                "jobId",            done.getJobId(),
                                "userId",           done.getUserId(),
                                "pageCount",        done.getPageCount(),
                                "color",            done.isColor(),
                                "priority",         done.getPriority(),
                                "waitingTimeMs",    done.getWaitingTimeMs(),
                                "turnaroundTimeMs", done.getTurnaroundTimeMs(),
                                "completedAt",      finishTime
                            ));
                        }
                    }

                } catch (InterruptedException e) {
                    // Simulation stopped mid-job — cancel current job
                    long stopTime = simulator.getClock().getSimulatedTimeMs();
                    PrintJob cur = printer.getCurrentJob();
                    if (cur != null) {
                        cur.markCancelled(stopTime);
                        simulator.getDatabase().record(cur);
                        simulator.publishEvent("JOB_CANCELLED", Map.of(
                            "jobId",  cur.getJobId(),
                            "reason", "Simulation stopped"
                        ));
                    }
                    throw e;
                }
            }
        } catch (InterruptedException e) {
            // Thread interrupted — exit cleanly
        }
    }
}
