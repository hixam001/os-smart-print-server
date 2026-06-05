package com.printscheduler.model;

import jakarta.validation.constraints.*;

/**
 * Configuration supplied by the client when starting or re-configuring a simulation.
 * All fields are validated before reaching the simulator.
 */
public class SimulationConfig {

    @Min(value = 1, message = "numUsers must be between 1 and 20")
    @Max(value = 20, message = "numUsers must be between 1 and 20")
    private int numUsers = 3;

    @Min(value = 1, message = "numPrinters must be between 1 and 20")
    @Max(value = 20, message = "numPrinters must be between 1 and 20")
    private int numPrinters = 2;

    @Min(value = 5, message = "queueCapacity must be between 5 and 100")
    @Max(value = 100, message = "queueCapacity must be between 5 and 100")
    private int queueCapacity = 10;

    @Min(value = 100, message = "jobIntervalMs must be between 100 and 10000")
    @Max(value = 10000, message = "jobIntervalMs must be between 100 and 10000")
    private long jobIntervalMs = 5000;   // users submit a job every ~5s

    @Pattern(regexp = "FCFS|SJF|HYBRID",
             message = "algorithm must be one of: FCFS, SJF, HYBRID")
    private String algorithm = "HYBRID";

    @DecimalMin(value = "0.0", message = "colorJobRatio must be between 0.0 and 1.0")
    @DecimalMax(value = "1.0", message = "colorJobRatio must be between 0.0 and 1.0")
    private double colorJobRatio = 0.5;

    @DecimalMin(value = "0.0", message = "smallJobPercentage must be between 0.0 and 1.0")
    @DecimalMax(value = "1.0", message = "smallJobPercentage must be between 0.0 and 1.0")
    private double smallJobPercentage = 0.3;

    @DecimalMin(value = "0.1", message = "simulationSpeed must be between 0.1 and 10.0")
    @DecimalMax(value = "10.0", message = "simulationSpeed must be between 0.1 and 10.0")
    private double simulationSpeed = 2.0; // 2× makes print delays watchable

    // ── Constructors ──────────────────────────────────────────────────────
    public SimulationConfig() {}

    // ── Getters / Setters ─────────────────────────────────────────────────
    public int    getNumUsers()           { return numUsers; }
    public void   setNumUsers(int v)      { this.numUsers = v; }

    public int    getNumPrinters()        { return numPrinters; }
    public void   setNumPrinters(int v)   { this.numPrinters = v; }

    public int    getQueueCapacity()      { return queueCapacity; }
    public void   setQueueCapacity(int v) { this.queueCapacity = v; }

    public long   getJobIntervalMs()      { return jobIntervalMs; }
    public void   setJobIntervalMs(long v){ this.jobIntervalMs = v; }

    public String getAlgorithm()          { return algorithm; }
    public void   setAlgorithm(String v)  { this.algorithm = v; }

    public double getColorJobRatio()      { return colorJobRatio; }
    public void   setColorJobRatio(double v) { this.colorJobRatio = v; }

    public double getSmallJobPercentage() { return smallJobPercentage; }
    public void   setSmallJobPercentage(double v) { this.smallJobPercentage = v; }

    public double getSimulationSpeed()    { return simulationSpeed; }
    public void   setSimulationSpeed(double v) { this.simulationSpeed = v; }
}
