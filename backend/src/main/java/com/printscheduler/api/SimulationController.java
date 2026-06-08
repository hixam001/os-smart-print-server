package com.printscheduler.api;

import com.printscheduler.api.dto.ApiResponse;
import com.printscheduler.api.dto.ConfigUpdateRequest;
import com.printscheduler.model.SimulationConfig;
import com.printscheduler.model.SimulationSnapshot;
import com.printscheduler.service.SimulationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private static final Logger log = LoggerFactory.getLogger(SimulationController.class);

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<Map<String, Object>>> start(
            @Valid @RequestBody SimulationConfig config) {

        log.info("POST /start — algo={} users={} printers={} capacity={}",
                config.getAlgorithm(), config.getNumUsers(),
                config.getNumPrinters(), config.getQueueCapacity());

        simulationService.start(config);

        Map<String, Object> data = Map.of(
                "status", "RUNNING",
                "algorithm", config.getAlgorithm(),
                "startedAt", System.currentTimeMillis());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(data));
    }

    @PostMapping("/pause")
    public ResponseEntity<ApiResponse<Map<String, Object>>> pause() {
        log.info("POST /pause");
        simulationService.pause();

        Map<String, Object> data = Map.of(
                "status", "PAUSED",
                "pausedAt", System.currentTimeMillis());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping("/resume")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resume() {
        log.info("POST /resume");
        simulationService.resume();

        Map<String, Object> data = Map.of(
                "status", "RUNNING",
                "resumedAt", System.currentTimeMillis());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping("/stop")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stop() {
        log.info("POST /stop");
        simulationService.stop();

        Map<String, Object> data = Map.of(
                "status", "STOPPED",
                "stoppedAt", System.currentTimeMillis());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reset() {
        log.info("POST /reset");
        simulationService.reset();

        Map<String, Object> data = Map.of(
                "status", "STOPPED",
                "resetAt", System.currentTimeMillis());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PutMapping("/configure")
    public ResponseEntity<ApiResponse<Map<String, Object>>> configure(
            @Valid @RequestBody ConfigUpdateRequest request) {

        log.info("PUT /configure — {}", request);
        simulationService.configure(request);

        Map<String, Object> applied = new java.util.LinkedHashMap<>();
        if (request.getAlgorithm() != null)
            applied.put("algorithm", request.getAlgorithm());
        if (request.getJobIntervalMs() != null)
            applied.put("jobIntervalMs", request.getJobIntervalMs());
        if (request.getSimulationSpeed() != null)
            applied.put("simulationSpeed", request.getSimulationSpeed());

        return ResponseEntity.ok(ApiResponse.ok(Map.of("appliedChanges", applied)));
    }

    @GetMapping("/state")
    public ResponseEntity<ApiResponse<SimulationSnapshot>> getState() {
        SimulationSnapshot snapshot = simulationService.getState();
        return ResponseEntity.ok(ApiResponse.ok(snapshot));
    }

    @GetMapping("/metrics")
    public ResponseEntity<ApiResponse<Object>> getMetrics(
            @RequestParam(defaultValue = "none") String groupBy,
            @RequestParam(defaultValue = "all") String timeRange) {

        SimulationSnapshot snapshot = simulationService.getState();

        Object metrics = snapshot.getDetails();
        return ResponseEntity.ok(ApiResponse.ok(
                metrics != null ? metrics : Map.of("message", "No metrics available yet")));
    }

    @GetMapping("/export")
    public ResponseEntity<?> exportData(
            @RequestParam String format,
            @RequestParam String type) {

        if (!format.equals("csv") && !format.equals("json")) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(new com.printscheduler.api.dto.ErrorDetail(
                            "INVALID_FORMAT", "format must be 'csv' or 'json'")));
        }
        if (!type.equals("jobs") && !type.equals("metrics")) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(new com.printscheduler.api.dto.ErrorDetail(
                            "INVALID_TYPE", "type must be 'jobs' or 'metrics'")));
        }

        if ("csv".equals(format)) {
            String csv = simulationService.exportJobsCsv();

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, "text/csv");
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + type + ".csv\"");
            return ResponseEntity.ok().headers(headers).body(csv);
        }

        Map<String, Object> data = Map.of(
                "type", type,
                "exportedAt", System.currentTimeMillis(),
                "totalRecords", 0,
                "records", java.util.List.of());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
