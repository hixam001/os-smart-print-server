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

/**
 * REST controller exposing all simulation lifecycle endpoints.
 *
 * <p>Base path: {@code /api/simulation}
 *
 * <table border="1" cellpadding="4">
 *   <tr><th>Method</th><th>Path</th><th>Action</th></tr>
 *   <tr><td>POST</td><td>/start</td><td>Start simulation with config</td></tr>
 *   <tr><td>POST</td><td>/pause</td><td>Pause running simulation</td></tr>
 *   <tr><td>POST</td><td>/resume</td><td>Resume paused simulation</td></tr>
 *   <tr><td>POST</td><td>/stop</td><td>Stop simulation (preserves metrics)</td></tr>
 *   <tr><td>POST</td><td>/reset</td><td>Stop + clear all state</td></tr>
 *   <tr><td>PUT</td><td>/configure</td><td>Change algo / speed at runtime</td></tr>
 *   <tr><td>GET</td><td>/state</td><td>Current simulation snapshot</td></tr>
 *   <tr><td>GET</td><td>/metrics</td><td>Performance metrics (delegates to core)</td></tr>
 *   <tr><td>GET</td><td>/export</td><td>Export job data as CSV or JSON</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private static final Logger log = LoggerFactory.getLogger(SimulationController.class);

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    // ── 1. Start ─────────────────────────────────────────────────────────

    /**
     * POST /api/simulation/start
     *
     * <p>Starts a new simulation. Returns 202 Accepted on success.
     */
    @PostMapping("/start")
    public ResponseEntity<ApiResponse<Map<String, Object>>> start(
            @Valid @RequestBody SimulationConfig config) {

        log.info("POST /start — algo={} users={} printers={} capacity={}",
            config.getAlgorithm(), config.getNumUsers(),
            config.getNumPrinters(), config.getQueueCapacity());

        simulationService.start(config);

        Map<String, Object> data = Map.of(
            "status",    "RUNNING",
            "algorithm", config.getAlgorithm(),
            "startedAt", System.currentTimeMillis()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(data));
    }

    // ── 2. Pause ─────────────────────────────────────────────────────────

    /** POST /api/simulation/pause */
    @PostMapping("/pause")
    public ResponseEntity<ApiResponse<Map<String, Object>>> pause() {
        log.info("POST /pause");
        simulationService.pause();

        Map<String, Object> data = Map.of(
            "status",   "PAUSED",
            "pausedAt", System.currentTimeMillis()
        );
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    // ── 3. Resume ────────────────────────────────────────────────────────

    /** POST /api/simulation/resume */
    @PostMapping("/resume")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resume() {
        log.info("POST /resume");
        simulationService.resume();

        Map<String, Object> data = Map.of(
            "status",    "RUNNING",
            "resumedAt", System.currentTimeMillis()
        );
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    // ── 4. Stop ──────────────────────────────────────────────────────────

    /** POST /api/simulation/stop */
    @PostMapping("/stop")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stop() {
        log.info("POST /stop");
        simulationService.stop();

        Map<String, Object> data = Map.of(
            "status",   "STOPPED",
            "stoppedAt", System.currentTimeMillis()
        );
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    // ── 5. Reset ─────────────────────────────────────────────────────────

    /** POST /api/simulation/reset — always succeeds (idempotent) */
    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reset() {
        log.info("POST /reset");
        simulationService.reset();

        Map<String, Object> data = Map.of(
            "status",  "STOPPED",
            "resetAt", System.currentTimeMillis()
        );
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    // ── 6. Configure ─────────────────────────────────────────────────────

    /** PUT /api/simulation/configure — change algorithm / speed at runtime */
    @PutMapping("/configure")
    public ResponseEntity<ApiResponse<Map<String, Object>>> configure(
            @Valid @RequestBody ConfigUpdateRequest request) {

        log.info("PUT /configure — {}", request);
        simulationService.configure(request);

        Map<String, Object> applied = new java.util.LinkedHashMap<>();
        if (request.getAlgorithm()       != null) applied.put("algorithm",      request.getAlgorithm());
        if (request.getJobIntervalMs()   != null) applied.put("jobIntervalMs",   request.getJobIntervalMs());
        if (request.getSimulationSpeed() != null) applied.put("simulationSpeed", request.getSimulationSpeed());

        return ResponseEntity.ok(ApiResponse.ok(Map.of("appliedChanges", applied)));
    }

    // ── 7. State ─────────────────────────────────────────────────────────

    /** GET /api/simulation/state — returns full current state snapshot */
    @GetMapping("/state")
    public ResponseEntity<ApiResponse<SimulationSnapshot>> getState() {
        SimulationSnapshot snapshot = simulationService.getState();
        return ResponseEntity.ok(ApiResponse.ok(snapshot));
    }

    // ── 8. Metrics ───────────────────────────────────────────────────────

    /**
     * GET /api/simulation/metrics
     *
     * <p>Delegates to the service for aggregated performance metrics.
     * The core team should override {@code getState().getDetails()} or
     * expose a dedicated metrics method on the service.
     *
     * @param groupBy   "none" | "printer" | "scheduler"
     * @param timeRange "all" | "last-hour" | "last-minute"
     */
    @GetMapping("/metrics")
    public ResponseEntity<ApiResponse<Object>> getMetrics(
            @RequestParam(defaultValue = "none")  String groupBy,
            @RequestParam(defaultValue = "all")   String timeRange) {

        SimulationSnapshot snapshot = simulationService.getState();
        // The core team sets snapshot.details to their metrics object
        Object metrics = snapshot.getDetails();
        return ResponseEntity.ok(ApiResponse.ok(
            metrics != null ? metrics : Map.of("message", "No metrics available yet")));
    }

    // ── 9. Export ────────────────────────────────────────────────────────

    /**
     * GET /api/simulation/export?format=csv&type=jobs
     *
     * <p>Core team: inject your job list here.
     * Currently returns a placeholder response.
     *
     * @param format "csv" or "json"
     * @param type   "jobs" or "metrics"
     */
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
            // ── TODO (Core Team): stream your completed jobs list here ─────
            String csv = "jobId,userId,pageCount,waitingTime,turnaroundTime\n";
            // for (PrintJob j : metricsCollector.getCompletedJobs()) { ... }
            // ─────────────────────────────────────────────────────────────

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, "text/csv");
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + type + ".csv\"");
            return ResponseEntity.ok().headers(headers).body(csv);
        }

        // JSON export
        Map<String, Object> data = Map.of(
            "type",          type,
            "exportedAt",    System.currentTimeMillis(),
            "totalRecords",  0,
            "records",       java.util.List.of()
        );
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
