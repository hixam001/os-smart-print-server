package com.printscheduler.model;

/**
 * Life-cycle state of a single print job.
 *
 * <pre>
 *  QUEUED → PRINTING → COMPLETED
 *                    ↘ FAILED
 *  QUEUED → CANCELLED
 * </pre>
 */
public enum JobStatus {
    /** Job is in the print queue, waiting for a free printer. */
    QUEUED,

    /** Job is currently being processed by a printer. */
    PRINTING,

    /** Job finished successfully. */
    COMPLETED,

    /** Job failed during printing (e.g. printer error). */
    FAILED,

    /** Job was removed from the queue before it could print. */
    CANCELLED
}
