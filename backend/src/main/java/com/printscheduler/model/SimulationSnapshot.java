package com.printscheduler.model;

public class SimulationSnapshot {

    private SimulationStatus status = SimulationStatus.STOPPED;
    private long             currentTimeMs = 0;
    private String           currentScheduler = "HYBRID";
    private double           simulationSpeed  = 1.0;
    private String           message = "";

    private Object details = null;

    public SimulationSnapshot() {}

    public SimulationSnapshot(SimulationStatus status, long currentTimeMs,
                               String currentScheduler, double simulationSpeed,
                               String message, Object details) {
        this.status           = status;
        this.currentTimeMs    = currentTimeMs;
        this.currentScheduler = currentScheduler;
        this.simulationSpeed  = simulationSpeed;
        this.message          = message;
        this.details          = details;
    }

    public SimulationStatus getStatus()            { return status; }
    public void setStatus(SimulationStatus s)      { this.status = s; }

    public long getCurrentTimeMs()                 { return currentTimeMs; }
    public void setCurrentTimeMs(long t)           { this.currentTimeMs = t; }

    public String getCurrentScheduler()            { return currentScheduler; }
    public void setCurrentScheduler(String s)      { this.currentScheduler = s; }

    public double getSimulationSpeed()             { return simulationSpeed; }
    public void setSimulationSpeed(double d)       { this.simulationSpeed = d; }

    public String getMessage()                     { return message; }
    public void setMessage(String m)               { this.message = m; }

    public Object getDetails()                     { return details; }
    public void setDetails(Object d)               { this.details = d; }
}
