package com.suko.vnc.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import com.suko.vnc.websocket.VNCWebSocketProxy;
import com.suko.vnc.security.VNCAuthService;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/api/proxy")
@Produces(MediaType.APPLICATION_JSON)
public class ProxyStatusResource {
    
    @Inject
    VNCWebSocketProxy webSocketProxy;
    
    @Inject
    VNCAuthService authService;
    
    @GET
    @Path("/status")
    public Map<String, Object> getProxyStatus() {
        return Map.of(
            "status", "UP",
            "phase", "Phase 4 - WebSocket Proxy",
            "activeConnections", webSocketProxy.getActiveConnectionCount(),
            "activeSessions", authService.getActiveSessionCount(),
            "timestamp", LocalDateTime.now().toString(),
            "proxyInfo", Map.of(
                "type", "VNC WebSocket Proxy",
                "version", "1.0.0",
                "features", new String[]{
                    "Session Validation",
                    "Bidirectional Data Forwarding", 
                    "Connection Statistics",
                    "Auto Cleanup",
                    "Error Handling"
                }
            )
        );
    }
    
    @GET
    @Path("/connections")
    public Map<String, Object> getActiveConnections() {
        var connections = webSocketProxy.getActiveConnections();
        
        return Map.of(
            "totalConnections", connections.size(),
            "connections", connections.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> {
                        var conn = entry.getValue();
                        return Map.of(
                            "userId", conn.authSession.userId,
                            "clientIP", conn.authSession.clientIP,
                            "duration", conn.getConnectionDuration(),
                            "bytesReceived", conn.bytesReceived,
                            "bytesSent", conn.bytesSent,
                            "isConnected", conn.isConnected,
                            "sessionCreated", conn.authSession.createdAt.toString()
                        );
                    }
                )),
            "timestamp", LocalDateTime.now().toString()
        );
    }
    
    @GET
    @Path("/stats")
    public Map<String, Object> getProxyStats() {
        var connections = webSocketProxy.getActiveConnections();
        
        long totalBytesReceived = connections.values().stream()
            .mapToLong(conn -> conn.bytesReceived)
            .sum();
            
        long totalBytesSent = connections.values().stream()
            .mapToLong(conn -> conn.bytesSent)
            .sum();
            
        double avgConnectionDuration = connections.values().stream()
            .mapToLong(conn -> conn.getConnectionDuration())
            .average()
            .orElse(0.0);
        
        return Map.of(
            "totalConnections", connections.size(),
            "totalBytesReceived", totalBytesReceived,
            "totalBytesSent", totalBytesSent,
            "totalBytesTransferred", totalBytesReceived + totalBytesSent,
            "averageConnectionDuration", Math.round(avgConnectionDuration),
            "activeSessions", authService.getActiveSessionCount(),
            "timestamp", LocalDateTime.now().toString()
        );
    }
}
