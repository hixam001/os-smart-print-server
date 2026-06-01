package com.printscheduler.exception;
public class SimulationNotPausedException extends SimulationException {
    public SimulationNotPausedException() {
        super("Cannot resume: simulation is not paused");
    }
}
