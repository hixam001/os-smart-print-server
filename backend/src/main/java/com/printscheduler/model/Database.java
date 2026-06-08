package com.printscheduler.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class Database {

    private final CopyOnWriteArrayList<PrintJob> allJobs = new CopyOnWriteArrayList<>();

    private final ConcurrentHashMap<Integer, CopyOnWriteArrayList<PrintJob>> byUser =
        new ConcurrentHashMap<>();

    public void record(PrintJob job) {
        if (job == null) return;
        JobStatus s = job.getStatus();
        if (s != JobStatus.COMPLETED && s != JobStatus.FAILED && s != JobStatus.CANCELLED) {
            return;
        }
        allJobs.add(job);
        byUser.computeIfAbsent(job.getUserId(), id -> new CopyOnWriteArrayList<>())
              .add(job);
    }

    public void reset() {
        allJobs.clear();
        byUser.clear();
    }

    public List<PrintJob> getAllJobs() {
        return Collections.unmodifiableList(new ArrayList<>(allJobs));
    }

    public List<PrintJob> getJobsByUser(int userId) {
        CopyOnWriteArrayList<PrintJob> userJobs = byUser.get(userId);
        return userJobs == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(userJobs));
    }

    public List<PrintJob> getCompletedJobs() {
        return allJobs.stream()
                      .filter(j -> j.getStatus() == JobStatus.COMPLETED)
                      .collect(Collectors.toList());
    }

    public List<PrintJob> getFailedJobs() {
        return allJobs.stream()
                      .filter(j -> j.getStatus() == JobStatus.FAILED)
                      .collect(Collectors.toList());
    }

    public List<PrintJob> getCancelledJobs() {
        return allJobs.stream()
                      .filter(j -> j.getStatus() == JobStatus.CANCELLED)
                      .collect(Collectors.toList());
    }

    public int totalJobCount() {
        return allJobs.size();
    }

    public SimulationState.Metrics buildMetrics(long elapsedMs) {
        SimulationState.Metrics m = new SimulationState.Metrics();

        List<PrintJob> completed = getCompletedJobs();
        List<PrintJob> failed    = getFailedJobs();
        List<PrintJob> cancelled = getCancelledJobs();

        m.setTotalJobsCompleted(completed.size());
        m.setTotalJobsFailed(failed.size());
        m.setTotalJobsCancelled(cancelled.size());

        if (!completed.isEmpty()) {
            OptionalDouble avgWait = completed.stream()
                .mapToLong(PrintJob::getWaitingTimeMs)
                .filter(v -> v >= 0)
                .average();
            m.setAvgWaitingTimeMs(avgWait.orElse(0.0));

            OptionalDouble avgTat = completed.stream()
                .mapToLong(PrintJob::getTurnaroundTimeMs)
                .filter(v -> v >= 0)
                .average();
            m.setAvgTurnaroundTimeMs(avgTat.orElse(0.0));

            OptionalDouble avgPages = completed.stream()
                .mapToInt(PrintJob::getPageCount)
                .average();
            m.setAvgPageCount(avgPages.orElse(0.0));

            long colorCount = completed.stream().filter(PrintJob::isColor).count();
            m.setColorJobRatio(completed.isEmpty() ? 0.0
                : (double) colorCount / completed.size());

            long elapsedMin = elapsedMs / 60_000;
            m.setThroughputJobsPerMin(elapsedMin > 0
                ? completed.size() / elapsedMin
                : completed.size());
        }

        return m;
    }

    public Map<Integer, Long> completedJobsPerUser() {
        return byUser.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().stream()
                      .filter(j -> j.getStatus() == JobStatus.COMPLETED)
                      .count()
            ));
    }

    public String toCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("jobId,userId,pageCount,color,priority,status,")
          .append("submittedAt,waitingTimeMs,turnaroundTimeMs\n");

        for (PrintJob j : allJobs) {
            sb.append(j.getJobId()).append(',')
              .append(j.getUserId()).append(',')
              .append(j.getPageCount()).append(',')
              .append(j.isColor()).append(',')
              .append(j.getPriority()).append(',')
              .append(j.getStatus().name()).append(',')
              .append(j.getSubmittedAt()).append(',')
              .append(j.getWaitingTimeMs()).append(',')
              .append(j.getTurnaroundTimeMs()).append('\n');
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("Database{total=%d, completed=%d, failed=%d, cancelled=%d}",
            allJobs.size(), getCompletedJobs().size(),
            getFailedJobs().size(), getCancelledJobs().size());
    }
}
