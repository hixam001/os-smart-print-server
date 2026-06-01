package com.printscheduler.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.printscheduler.model.SimulationSnapshot;
import com.printscheduler.service.SimulationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Periodically reads the current simulation state and broadcasts it to all
 * connected WebSocket clients.
 *
 * <h3>Broadcast protocol</h3>
 * <ul>
 *   <li><b>STATE_UPDATE</b> — sent every {@code broadcaster.interval-ms} (default 100 ms)
 *       when state actually changed (delta detected via hash).</li>
 *   <li><b>HEARTBEAT</b> — sent every {@code broadcaster.heartbeat-interval-ms}
 *       (default 30 s) when no state change has occurred.</li>
 * </ul>
 */
@Component
public class SimulationBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(SimulationBroadcaster.class);

    private final SimulationService          simulationService;
    private final SimulationWebSocketHandler wsHandler;
    private final ObjectMapper               objectMapper;

    @Value("${simulation.broadcaster.heartbeat-interval-ms:30000}")
    private long heartbeatIntervalMs;

    /** Hash of the last broadcast payload — used for delta detection. */
    private int  lastStateHash          = 0;
    private long lastBroadcastTimestamp = 0;

    public SimulationBroadcaster(SimulationService simulationService,
                                  SimulationWebSocketHandler wsHandler,
                                  ObjectMapper objectMapper) {
        this.simulationService = simulationService;
        this.wsHandler         = wsHandler;
        this.objectMapper      = objectMapper;
    }

    // ── Scheduled broadcast ───────────────────────────────────────────────

    /**
     * Runs every 100 ms (fixed rate from application.yml).
     * Broadcasts STATE_UPDATE if state changed, otherwise sends HEARTBEAT
     * every 30 seconds to keep connections alive.
     */
    @Scheduled(fixedRateString = "${simulation.broadcaster.interval-ms:100}")
    public void broadcastState() {
        if (wsHandler.getConnectionCount() == 0) return;  // fast-path: nobody listening

        try {
            SimulationSnapshot snapshot = simulationService.getState();
            String json = buildStateUpdateJson(snapshot);

            int newHash = json.hashCode();
            long now    = System.currentTimeMillis();

            if (newHash != lastStateHash) {
                // State changed — send STATE_UPDATE
                wsHandler.broadcast(json);
                lastStateHash          = newHash;
                lastBroadcastTimestamp = now;

            } else if ((now - lastBroadcastTimestamp) >= heartbeatIntervalMs) {
                // No change for 30 s — send HEARTBEAT to keep connection alive
                wsHandler.broadcast(buildHeartbeatJson(now));
                lastBroadcastTimestamp = now;
            }

        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize simulation state for broadcast", ex);
            sendErrorToClients("STATE_SERIALIZATION_ERROR", ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected broadcaster error", ex);
        }
    }

    // ── Message builders ──────────────────────────────────────────────────

    /**
     * Sends an EVENT message immediately (not batched) — called by the core
     * team's simulator after significant events (job queued, completed, etc.).
     *
     * <p>Example usage from SimulationServiceImpl or PrinterThread:
     * <pre>
     *   broadcaster.publishEvent("COMPLETED", Map.of("jobId", "Job-42", ...));
     * </pre>
     */
    public void publishEvent(String eventType, Map<String, Object> eventDetails) {
        try {
            Map<String, Object> envelope = Map.of(
                "type",      "EVENT",
                "timestamp", System.currentTimeMillis(),
                "event",     Map.of(
                    "eventType", eventType,
                    "details",   eventDetails
                )
            );
            wsHandler.broadcast(objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException ex) {
            log.error("Failed to publish event {} to WebSocket", eventType, ex);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private String buildStateUpdateJson(SimulationSnapshot snapshot)
            throws JsonProcessingException {
        Map<String, Object> envelope = Map.of(
            "type",      "STATE_UPDATE",
            "timestamp", System.currentTimeMillis(),
            "data",      snapshot
        );
        return objectMapper.writeValueAsString(envelope);
    }

    private String buildHeartbeatJson(long now) throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
            "type",      "HEARTBEAT",
            "timestamp", now
        ));
    }

    private void sendErrorToClients(String code, String message) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                "type",  "ERROR",
                "timestamp", System.currentTimeMillis(),
                "error", Map.of("code", code, "message", message)
            ));
            wsHandler.broadcast(json);
        } catch (JsonProcessingException ex) {
            log.error("Could not even serialize the error message", ex);
        }
    }
}
