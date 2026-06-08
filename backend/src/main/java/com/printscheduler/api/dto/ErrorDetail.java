package com.printscheduler.api.dto;

import java.util.Map;

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
