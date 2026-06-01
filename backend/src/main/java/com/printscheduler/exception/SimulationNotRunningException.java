package com.printscheduler.exception;
public class SimulationNotRunningException extends SimulationException {
    public SimulationNotRunningException(String action) {
        super("Cannot %s: simulation is not running".formatted(action));
    }
}
