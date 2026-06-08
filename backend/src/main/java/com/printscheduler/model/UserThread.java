package com.printscheduler.model;

import com.printscheduler.service.PrintServerSimulator;
import java.util.Map;
import java.util.Random;

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

            Thread.sleep(random.nextInt(500) + 100);

            while (simulator.isRunning()) {
                simulator.getPauseCoordinator().checkPause();

                long interval = simulator.getConfig().getJobIntervalMs();

                long jitter = (long) (interval * 0.3 * (random.nextDouble() - 0.5));
                long sleepTimeMs = Math.max(100, interval + jitter);

                simulator.getClock().sleepSimulated(sleepTimeMs, simulator::isRunning, simulator.getPauseCoordinator());

                boolean isColor = random.nextDouble() < simulator.getConfig().getColorJobRatio();
                double smallJobPercentage = simulator.getConfig().getSmallJobPercentage();

                int pageCount;
                if (random.nextDouble() < smallJobPercentage) {
                    pageCount = random.nextInt(5) + 1;
                } else {
                    pageCount = random.nextInt(25) + 6;
                }

                int priority = random.nextInt(5) + 1;
                long nowMs = simulator.getClock().getSimulatedTimeMs();

                PrintJob job = new PrintJob(userId, pageCount, isColor, priority, nowMs);

                boolean enqueued = simulator.getQueue().enqueue(job);
                if (enqueued) {

                    simulator.getJobsAvailableSemaphore().release();

                    simulator.publishEvent("JOB_SUBMITTED", Map.of(
                        "jobId", job.getJobId(),
                        "userId", userId,
                        "pageCount", pageCount,
                        "isColor", isColor,
                        "priority", priority,
                        "submittedAt", nowMs
                    ));
                } else {

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

        }
    }
}
