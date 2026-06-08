package com.printscheduler.api.dto;

import jakarta.validation.constraints.*;

public class ConfigUpdateRequest {

    @Pattern(regexp = "FCFS|SJF|HYBRID",
             message = "algorithm must be one of: FCFS, SJF, HYBRID")
    private String algorithm;

    @Min(value = 100, message = "jobIntervalMs must be between 100 and 10000")
    @Max(value = 10000, message = "jobIntervalMs must be between 100 and 10000")
    private Long jobIntervalMs;

    @DecimalMin(value = "0.1", message = "simulationSpeed must be between 0.1 and 10.0")
    @DecimalMax(value = "10.0", message = "simulationSpeed must be between 0.1 and 10.0")
    private Double simulationSpeed;

    public String  getAlgorithm()            { return algorithm; }
    public void    setAlgorithm(String v)    { this.algorithm = v; }

    public Long    getJobIntervalMs()        { return jobIntervalMs; }
    public void    setJobIntervalMs(Long v)  { this.jobIntervalMs = v; }

    public Double  getSimulationSpeed()      { return simulationSpeed; }
    public void    setSimulationSpeed(Double v) { this.simulationSpeed = v; }
}
