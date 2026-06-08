package com.printscheduler.api;

import com.printscheduler.api.dto.ApiResponse;
import com.printscheduler.api.dto.ErrorDetail;
import com.printscheduler.exception.InvalidConfigurationException;
import com.printscheduler.exception.SimulationAlreadyRunningException;
import com.printscheduler.exception.SimulationException;
import com.printscheduler.exception.SimulationNotPausedException;
import com.printscheduler.exception.SimulationNotRunningException;
import com.printscheduler.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(SimulationAlreadyRunningException.class)
    public ResponseEntity<ApiResponse<Void>> handleAlreadyRunning(
            SimulationAlreadyRunningException ex) {
        log.warn("409 – {}", ex.getMessage());
        return conflict("SIMULATION_ALREADY_RUNNING", ex.getMessage());
    }

    @ExceptionHandler(SimulationNotRunningException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotRunning(
            SimulationNotRunningException ex) {
        log.warn("409 – {}", ex.getMessage());
        return conflict("SIMULATION_NOT_RUNNING", ex.getMessage());
    }

    @ExceptionHandler(SimulationNotPausedException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotPaused(
            SimulationNotPausedException ex) {
        log.warn("409 – {}", ex.getMessage());
        return conflict("SIMULATION_NOT_PAUSED", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException ex) {
        Map<String, Object> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        log.warn("400 – validation failed: {}", fieldErrors);
        return badRequest("VALIDATION_FAILED", "Request validation failed", fieldErrors);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomValidation(
            ValidationException ex) {
        Map<String, Object> fieldErrors = new HashMap<>(ex.getFieldErrors());
        log.warn("400 – {}", ex.getMessage());
        return badRequest("VALIDATION_FAILED", ex.getMessage(), fieldErrors);
    }

    @ExceptionHandler(InvalidConfigurationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidConfig(
            InvalidConfigurationException ex) {
        log.warn("400 – {}", ex.getMessage());
        return badRequest("INVALID_CONFIGURATION", ex.getMessage(), Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(
            HttpMessageNotReadableException ex) {
        log.warn("400 – malformed request body: {}", ex.getMessage());
        return badRequest("MALFORMED_REQUEST", "Request body is missing or malformed", Map.of());
    }

    @ExceptionHandler(SimulationException.class)
    public ResponseEntity<ApiResponse<Void>> handleSimulation(SimulationException ex) {
        log.error("500 – unhandled SimulationException: {}", ex.getMessage(), ex);
        return internalError("SIMULATION_ERROR", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("500 – unexpected error", ex);
        return internalError("INTERNAL_ERROR", "An unexpected error occurred");
    }

    private ResponseEntity<ApiResponse<Void>> conflict(String code, String message) {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(new ErrorDetail(code, message)));
    }

    private ResponseEntity<ApiResponse<Void>> badRequest(
            String code, String message, Map<String, Object> details) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(new ErrorDetail(code, message, details)));
    }

    private ResponseEntity<ApiResponse<Void>> internalError(String code, String message) {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(new ErrorDetail(code, message)));
    }
}
