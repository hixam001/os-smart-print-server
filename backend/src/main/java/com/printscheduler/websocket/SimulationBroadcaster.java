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

@Component
public class SimulationBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(SimulationBroadcaster.class);

    private final SimulationService          simulationService;
    private final SimulationWebSocketHandler wsHandler;
    private final ObjectMapper               objectMapper;

    @Value("${simulation.broadcaster.heartbeat-interval-ms:30000}")
    private long heartbeatIntervalMs;

    private int  lastStateHash          = 0;
    private long lastBroadcastTimestamp = 0;

    public SimulationBroadcaster(SimulationService simulationService,
                                  SimulationWebSocketHandler wsHandler,
                                  ObjectMapper objectMapper) {
        this.simulationService = simulationService;
        this.wsHandler         = wsHandler;
        this.objectMapper      = objectMapper;
    }

    @Scheduled(fixedRateString = "${simulation.broadcaster.interval-ms:100}")
    public void broadcastState() {
        if (wsHandler.getConnectionCount() == 0) return;

        try {
            SimulationSnapshot snapshot = simulationService.getState();
            String json = buildStateUpdateJson(snapshot);

            int newHash = json.hashCode();
            long now    = System.currentTimeMillis();

            if (newHash != lastStateHash) {

                wsHandler.broadcast(json);
                lastStateHash          = newHash;
                lastBroadcastTimestamp = now;

            } else if ((now - lastBroadcastTimestamp) >= heartbeatIntervalMs) {

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
