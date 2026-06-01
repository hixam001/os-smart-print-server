package com.printscheduler.api.dto;

import java.util.Map;

/**
 * Structured error payload included in every failed API response.
 *
 * <pre>
 * {
 *   "code": "SIMULATION_ALREADY_RUNNING",
 *   "message": "Cannot start: simulation is already running",
 *   "details": { }
 * }
 * </pre>
 */
public class ErrorDetail {

    private final String              code;
    private final String              message;
    private final Map<String, Object> details;

    public ErrorDetail(String code, String message, Map<String, Object> details) {
        this.code    = code;
        this.message = message;
        this.details = details == null ? Map.of() : details;
    }

    public ErrorDetail(String code, String message) {
        this(code, message, Map.of());
    }

    public String              getCode()    { return code; }
    public String              getMessage() { return message; }
    public Map<String, Object> getDetails() { return details; }
}
