package com.printscheduler.exception;
public class SchedulerException extends SimulationException {
    public SchedulerException(String message)                  { super(message); }
    public SchedulerException(String message, Throwable cause) { super(message, cause); }
}
