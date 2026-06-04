package com.printscheduler.model;

import com.printscheduler.service.PrintServerSimulator;
import java.util.Map;
import java.util.Random;

/**
 * Simulates a single user submitting print jobs to the print queue.
 */
public class UserThread extends Thread {
    private final int userId;
    private final PrintServerSimulator simulator;
    private final Random random = new Random();

    public UserThread(int userId, PrintServerSimulator simulator) {
        super("UserThread-" + userId);
        this.userId = userId;
        this.simulator = simulator;
    }

    @Override
    public void run() {
        try {
            // Add a small initial random delay so that all user threads don't submit simultaneously at time 0
            Thread.sleep(random.nextInt(500) + 100);

            while (simulator.isRunning()) {
                simulator.getPauseCoordinator().checkPause();

                // Compute sleep time based on configuration and current simulation speed
                long interval = simulator.getConfig().getJobIntervalMs();
                // Add jitter to make arrival times realistic (+/- 15% deviation)
                long jitter = (long) (interval * 0.3 * (random.nextDouble() - 0.5));
                long sleepTimeMs = Math.max(100, interval + jitter);

                // Use the custom simulation clock to sleep in a pause/speed-aware manner
                simulator.getClock().sleepSimulated(sleepTimeMs, simulator::isRunning, simulator.getPauseCoordinator());

                // Generate job properties randomly according to simulation configuration
                boolean isColor = random.nextDouble() < simulator.getConfig().getColorJobRatio();
                double smallJobPercentage = simulator.getConfig().getSmallJobPercentage();
                
                int pageCount;
                if (random.nextDouble() < smallJobPercentage) {
                    pageCount = random.nextInt(5) + 1; // Small job: 1 to 5 pages
                } else {
                    pageCount = random.nextInt(25) + 6; // Large job: 6 to 30 pages
                }
                
                int priority = random.nextInt(5) + 1; // Priority: 1 to 5 (higher is more urgent)
                long nowMs = simulator.getClock().getSimulatedTimeMs();

                PrintJob job = new PrintJob(userId, pageCount, isColor, priority, nowMs);

                // Enqueue job into print queue
                boolean enqueued = simulator.getQueue().enqueue(job);
                if (enqueued) {
                    // Release the CountingSemaphore to wake up an idle PrinterThread
                    simulator.getJobsAvailableSemaphore().release();

                    // Publish event via WebSocket broadcaster
                    simulator.publishEvent("JOB_SUBMITTED", Map.of(
                        "jobId", job.getJobId(),
                        "userId", userId,
                        "pageCount", pageCount,
                        "isColor", isColor,
                        "priority", priority,
                        "submittedAt", nowMs
                    ));
                } else {
                    // Log queue rejection event (queue capacity exceeded)
                    simulator.publishEvent("JOB_REJECTED", Map.of(
                        "userId", userId,
                        "pageCount", pageCount,
                        "isColor", isColor,
                        "priority", priority,
                        "rejectedAt", nowMs
                    ));
                }
            }
        } catch (InterruptedException e) {
            // Exit thread cleanly when interrupted by stop()
        }
    }
}
