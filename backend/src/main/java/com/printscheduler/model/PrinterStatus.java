package com.printscheduler.model;

/**
 * Lifecycle state of a single physical printer.
 *
 * <pre>
 *  IDLE → BUSY → IDLE
 *  BUSY → ERROR
 *  ERROR → IDLE  (after operator clear)
 *  * → OFFLINE   (when printer is removed from the simulation)
 * </pre>
 */
public enum PrinterStatus {
    /** Printer is available and waiting for work. */
    IDLE,

    /** Printer is actively processing a job. */
    BUSY,

    /** Printer encountered a hardware/paper/ink error. */
    ERROR,

    /** Printer is not participating in the simulation. */
    OFFLINE
}
