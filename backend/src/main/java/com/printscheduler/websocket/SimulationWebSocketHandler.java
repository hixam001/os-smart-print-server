package com.printscheduler.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SimulationWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SimulationWebSocketHandler.class);

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    private final ObjectMapper objectMapper;

    public SimulationWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        String clientId = UUID.randomUUID().toString();
        session.getAttributes().put("clientId", clientId);

        String ack = objectMapper.writeValueAsString(Map.of(
            "type",      "CONNECTED",
            "timestamp", System.currentTimeMillis(),
            "clientId",  clientId,
            "message",   "Connected to simulation broker"
        ));
        session.sendMessage(new TextMessage(ack));
        log.info("WebSocket client connected: {} (total: {})", clientId, sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket client disconnected (total: {}) — {}", sessions.size(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session);
        log.warn("WebSocket transport error — session removed: {}", exception.getMessage());
    }

    public void broadcast(String json) {
        if (sessions.isEmpty()) return;

        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(message);
                }
            } catch (IOException ex) {
                log.warn("Failed to send to client — removing session: {}", ex.getMessage());
                sessions.remove(session);
            }
        }
    }

    public int getConnectionCount() { return sessions.size(); }
}
