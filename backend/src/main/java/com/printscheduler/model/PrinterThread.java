package com.printscheduler.model;

import com.printscheduler.service.PrintServerSimulator;
import java.util.Map;
import java.util.Random;

/**
 * Simulates a printer that consumes print jobs from the print queue.
 * Uses CountingSemaphore to wait for jobs to become available.
 */
public class PrinterThread extends Thread {
    private final Printer printer;
    private final PrintServerSimulator simulator;
    private final Random random = new Random();

    public PrinterThread(Printer printer, PrintServerSimulator simulator) {
        super("PrinterThread-" + printer.getPrinterId());
        this.printer = printer;
        this.simulator = simulator;
    }

    @Override
    public void run() {
        try {
            while (simulator.isRunning()) {
                simulator.getPauseCoordinator().checkPause();

                // Block until a job is enqueued (Producer-Consumer synchronization using CountingSemaphore)
                simulator.getJobsAvailableSemaphore().acquire();

                // Post-acquire safety check (e.g. if simulation stopped while waiting)
                if (!simulator.isRunning()) {
                    break;
                }
                simulator.getPauseCoordinator().checkPause();

                // Dequeue the next job according to active scheduling algorithm (FCFS, SJF, HYBRID)
                long nowMs = simulator.getClock().getSimulatedTimeMs();
                PrintJob job = simulator.getQueue().dequeue(nowMs);
                
                if (job == null) {
                    continue; // Queue was cleared or modified
                }

                // Start print process
                long durationMs = job.estimatedDurationMs(PrintQueue.MS_PER_PAGE);
                printer.startJob(job, nowMs, durationMs);

                simulator.publishEvent("PRINT_START", Map.of(
                    "printerId", printer.getPrinterId(),
                    "printerName", printer.getName(),
                    "jobId", job.getJobId(),
                    "userId", job.getUserId(),
                    "pageCount", job.getPageCount(),
                    "startedAt", nowMs
                ));

                try {
                    // Simulate printing time (pause-aware and speed-adjusted)
                    simulator.getClock().sleepSimulated(durationMs, simulator::isRunning, simulator.getPauseCoordinator());

                    // Introduce a 5% chance of a hardware failure / paper jam
                    if (random.nextDouble() < 0.05) {
                        long failTime = simulator.getClock().getSimulatedTimeMs();
                        PrintJob failedJob = printer.failJob(failTime);
                        
                        if (failedJob != null) {
                            simulator.getDatabase().record(failedJob);
                            simulator.publishEvent("PRINT_FAIL", Map.of(
                                "printerId", printer.getPrinterId(),
                                "printerName", printer.getName(),
                                "jobId", failedJob.getJobId(),
                                "failedAt", failTime
                            ));
                        }

                        // Simulate repair duration (e.g., 5 seconds of simulated time)
                        simulator.getClock().sleepSimulated(5000L, simulator::isRunning, simulator.getPauseCoordinator());

                        // Clear error and return printer to IDLE
                        printer.clearError();
                        simulator.publishEvent("PRINTER_RECOVER", Map.of(
                            "printerId", printer.getPrinterId(),
                            "printerName", printer.getName(),
                            "status", "IDLE"
                        ));
                    } else {
                        // Successfully print the job
                        long finishTime = simulator.getClock().getSimulatedTimeMs();
                        PrintJob completedJob = printer.finishJob(finishTime);
                        
                        if (completedJob != null) {
                            simulator.getDatabase().record(completedJob);
                            simulator.publishEvent("PRINT_COMPLETED", Map.of(
                                "printerId", printer.getPrinterId(),
                                "printerName", printer.getName(),
                                "jobId", completedJob.getJobId(),
                                "userId", completedJob.getUserId(),
                                "completedAt", finishTime
                            ));
                        }
                    }
                } catch (InterruptedException e) {
                    // Handle print interruption (simulation stopped mid-job)
                    long stopTime = simulator.getClock().getSimulatedTimeMs();
                    PrintJob cancelledJob = printer.getCurrentJob();
                    if (cancelledJob != null) {
                        cancelledJob.markCancelled(stopTime);
                        simulator.getDatabase().record(cancelledJob);
                    }
                    throw e; // Terminate thread loop
                }
            }
        } catch (InterruptedException e) {
            // Exit thread cleanly when interrupted
        }
    }
}
