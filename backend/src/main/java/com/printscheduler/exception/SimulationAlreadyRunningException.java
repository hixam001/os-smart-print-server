package com.printscheduler.exception;
public class SimulationAlreadyRunningException extends SimulationException {
    public SimulationAlreadyRunningException() {
        super("Cannot start: simulation is already running");
    }
}
