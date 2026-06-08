package com.printscheduler.api;

import com.printscheduler.api.dto.ApiResponse;
import com.printscheduler.service.SimulationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final SimulationService simulationService;

    public HealthController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();

        Map<String, Object> components = Map.of(
            "simulator", simulationService.isRunning() ? "RUNNING"
                       : simulationService.isPaused()  ? "PAUSED" : "STOPPED",
            "websocket", "UP",
            "api",       "UP"
        );

        Map<String, Object> data = Map.of(
            "status",     "UP",
            "timestamp",  System.currentTimeMillis(),
            "uptimeMs",   uptimeMs,
            "components", components
        );

        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
