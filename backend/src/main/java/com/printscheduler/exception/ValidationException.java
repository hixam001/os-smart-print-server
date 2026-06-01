package com.printscheduler.exception;
import java.util.Map;
public class ValidationException extends RuntimeException {
    private final Map<String, String> fieldErrors;
    public ValidationException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = fieldErrors;
    }
    public ValidationException(String message) { this(message, Map.of()); }
    public Map<String, String> getFieldErrors() { return fieldErrors; }
}
