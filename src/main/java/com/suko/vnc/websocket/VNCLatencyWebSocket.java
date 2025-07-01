package com.suko.vnc.websocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.suko.vnc.security.VNCAuthService;

import io.quarkus.websockets.next.CloseReason;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnError;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.PathParam;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;

@WebSocket(path = "/latency/{sessionId}")
public class VNCLatencyWebSocket {
    
    private static final Logger log = LoggerFactory.getLogger(VNCLatencyWebSocket.class);
    
    @Inject
    VNCAuthService authService;
    
    @Inject
    VNCConnectionManager connectionManager;
    
    // Store ping timestamps for each connection
    private final Map<String, Long> pingTimestamps = new ConcurrentHashMap<>();
    
    @OnOpen
    public void onOpen(WebSocketConnection connection, @PathParam String sessionId) {
        log.debug("Latency WebSocket connection opened for session: {}", sessionId);
        
        VNCAuthService.VNCSession vncSession = authService.getSession(sessionId);
        if (vncSession == null) {
            log.warn("Invalid session for latency measurement: {}", sessionId);
            connection.closeAndAwait(new CloseReason(1008, "Invalid session"));
            return;
        }
    }
    
    @OnTextMessage
    public void onTextMessage(String message, WebSocketConnection connection, @PathParam String sessionId) {
        try {
            JsonObject jsonMessage = Json.createReader(new java.io.StringReader(message)).readObject();
            String type = jsonMessage.getString("type", "");
            
            switch (type) {
                case "ping":
                    handlePing(connection, sessionId, jsonMessage);
                    break;
                case "pong":
                    handlePong(connection, sessionId, jsonMessage);
                    break;
                default:
                    log.warn("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            log.error("Error processing latency message for session: {}", sessionId, e);
        }
    }
    
    private void handlePing(WebSocketConnection connection, String sessionId, JsonObject message) {
        long clientTimestamp = message.getJsonNumber("timestamp").longValue();
        long serverTimestamp = System.currentTimeMillis();
        
        // Validate client timestamp
        if (clientTimestamp <= 0 || clientTimestamp > serverTimestamp) {
            log.warn("Invalid client timestamp {} for session: {}", clientTimestamp, sessionId);
            return;
        }
        
        // Check if timestamp is too old (more than 1 minute)
        if (serverTimestamp - clientTimestamp > 60000) {
            log.warn("Client timestamp too old for session: {}", sessionId);
            return;
        }
        
        // Store the ping timestamp
        pingTimestamps.put(sessionId, clientTimestamp);
        
        // Send pong back immediately
        JsonObject pong = Json.createObjectBuilder()
            .add("type", "pong")
            .add("clientTimestamp", clientTimestamp)
            .add("serverTimestamp", serverTimestamp)
            .build();
        
        connection.sendText(pong.toString()).subscribe().with(
            success -> log.debug("Pong sent for session: {}", sessionId),
            failure -> log.error("Failed to send pong for session: {}", sessionId, failure)
        );
    }    
    private void handlePong(WebSocketConnection connection, String sessionId, JsonObject message) {
        // This is a response to our ping, calculate round-trip time
        long clientTimestamp = message.getJsonNumber("clientTimestamp").longValue();
        long currentTime = System.currentTimeMillis();
        
        // Calculate round-trip time (client -> server -> client)
        long roundTripTime = currentTime - clientTimestamp;
        
        // Calculate one-way latency (approximation: half of round-trip time)
        long oneWayLatency = roundTripTime / 2;
        
        // Update the connection with browser-to-proxy latency
        VNCConnection vncConnection = connectionManager.getConnection(sessionId);
        if (vncConnection != null) {
            vncConnection.setBrowserToProxyLatency(oneWayLatency);
            vncConnection.setLastLatencyUpdate(System.currentTimeMillis());
            log.debug("Updated browser-to-proxy latency for session {}: {}ms", sessionId, oneWayLatency);
        }
    }
    
    @OnClose
    public void onClose(WebSocketConnection connection, @PathParam String sessionId) {
        log.debug("Latency WebSocket connection closed for session: {}", sessionId);
        pingTimestamps.remove(sessionId);
    }
    
    @OnError
    public void onError(WebSocketConnection connection, Throwable throwable, @PathParam String sessionId) {
        log.error("Latency WebSocket error for session: {}", sessionId, throwable);
        pingTimestamps.remove(sessionId);
    }
} 