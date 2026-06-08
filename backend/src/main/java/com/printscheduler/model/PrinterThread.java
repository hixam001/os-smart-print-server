package com.printscheduler.model;

import com.printscheduler.service.PrintServerSimulator;
import java.util.Map;
import java.util.Random;

public class PrinterThread extends Thread {

    private static final long WARMUP_MS = 500L;

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

            simulator.getClock().sleepSimulated(WARMUP_MS, simulator::isRunning,
                    simulator.getPauseCoordinator());

            while (simulator.isRunning()) {
                simulator.getPauseCoordinator().checkPause();

                simulator.getJobsAvailableSemaphore().acquire();

                if (!simulator.isRunning()) break;
                simulator.getPauseCoordinator().checkPause();

                long nowMs = simulator.getClock().getSimulatedTimeMs();
                PrintJob job = simulator.getQueue().dequeue(nowMs);
                if (job == null) continue;

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

                    boolean failed = false;
                    for (int page = 1; page <= job.getPageCount(); page++) {
                        if (!simulator.isRunning()) throw new InterruptedException();
                        simulator.getPauseCoordinator().checkPause();

                        simulator.getClock().sleepSimulated(
                            PrintQueue.MS_PER_PAGE,
                            simulator::isRunning,
                            simulator.getPauseCoordinator()
                        );

                        if (page < job.getPageCount()) {
                            simulator.getClock().sleepSimulated(
                                300L,
                                simulator::isRunning,
                                simulator.getPauseCoordinator()
                            );
                        }

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

        }
    }
}
