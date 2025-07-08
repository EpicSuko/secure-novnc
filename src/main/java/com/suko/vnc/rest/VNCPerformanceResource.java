package com.suko.vnc.rest;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.suko.vnc.websocket.VNCConnection;
import com.suko.vnc.websocket.VNCConnectionManager;
import com.suko.vnc.websocket.VNCPerformanceMonitor;
import com.suko.vnc.security.VNCAuthService;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/vnc/performance")
public class VNCPerformanceResource {
    
    @Inject
    VNCConnectionManager connectionManager;
    
    @Inject
    VNCPerformanceMonitor performanceMonitor;
    
    @Inject
    VNCAuthService authService;
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPerformanceStats(@QueryParam("sessionId") String sessionId) {
        try {
            // Validate session
            if (sessionId == null || sessionId.trim().isEmpty()) {
                Map<String, String> error = Map.of("error", "Session ID is required");
                return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
            }
            
            VNCAuthService.VNCSession session = authService.getSession(sessionId);
            if (session == null) {
                Map<String, String> error = Map.of("error", "Invalid or expired session");
                return Response.status(Response.Status.UNAUTHORIZED).entity(error).build();
            }
            
            VNCPerformanceMonitor.PerformanceStats stats = performanceMonitor.getPerformanceStats();

            Map<String, VNCConnection> userConnections = getUserConnections(sessionId);
            
            // Create detailed connection stats for user's connections only
            Map<String, Object> connectionDetails = userConnections.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> {
                        VNCConnection conn = entry.getValue();
                        Map<String, Object> connStats = new HashMap<>();
                        connStats.put("state", conn.state.toString());
                        connStats.put("connected", conn.isConnected);
                        connStats.put("duration", conn.getConnectionDuration());
                        connStats.put("bytesReceived", conn.bytesReceived);
                        connStats.put("bytesSent", conn.bytesSent);
                        connStats.put("messageCount", conn.messageCount);
                        connStats.put("averageLatency", conn.getAverageLatency());
                        connStats.put("browserToProxyLatency", conn.browserToProxyLatency);
                        connStats.put("proxyToVNCLatency", conn.proxyToVNCLatency);
                        connStats.put("totalEndToEndLatency", conn.getTotalEndToEndLatency());
                        connStats.put("throughput", conn.getThroughput());
                        connStats.put("lastActivity", conn.lastActivityTime);
                        connStats.put("lastLatencyUpdate", conn.lastLatencyUpdate);
                        connStats.put("clientBufferSize", conn.clientHandler != null ? conn.clientHandler.getBufferSize() : 0);
                        connStats.put("serverBufferSize", conn.serverHandler != null ? conn.serverHandler.getBufferSize() : 0);
                        return connStats;
                    }
                ));
            
            // Calculate user-specific stats
            long userBytesReceived = userConnections.values().stream().mapToLong(conn -> conn.bytesReceived).sum();
            long userBytesSent = userConnections.values().stream().mapToLong(conn -> conn.bytesSent).sum();
            long userMessages = userConnections.values().stream().mapToLong(conn -> conn.messageCount).sum();
            double userAverageLatency = userConnections.values().stream()
                .mapToDouble(conn -> conn.getAverageLatency())
                .average()
                .orElse(0.0);
            double userThroughput = userConnections.values().stream()
                .mapToDouble(conn -> conn.getThroughput())
                .sum();
            
            Map<String, Object> response = new HashMap<>();
            response.put("timestamp", stats.timestamp);
            response.put("userId", session.getUserId());
            response.put("userConnections", userConnections.size());
            response.put("totalConnections", stats.totalConnections);
            response.put("userBytesReceived", userBytesReceived);
            response.put("userBytesSent", userBytesSent);
            response.put("userMessages", userMessages);
            response.put("userAverageLatency", userAverageLatency);
            response.put("userThroughput", userThroughput);
            response.put("connections", connectionDetails);
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            Map<String, String> error = Map.of("error", "Failed to get performance stats: " + e.getMessage());
            return Response.serverError().entity(error).build();
        }
    }
    
    @GET
    @Path("/summary")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getPerformanceSummary(@QueryParam("sessionId") String sessionId) {
        try {
            // Validate session
            if (sessionId == null || sessionId.trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Session ID is required")
                    .build();
            }
            
            VNCAuthService.VNCSession session = authService.getSession(sessionId);
            if (session == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Invalid or expired session")
                    .build();
            }
            
            VNCPerformanceMonitor.PerformanceStats stats = performanceMonitor.getPerformanceStats();

            Map<String, VNCConnection> userConnections = getUserConnections(sessionId);
            
            // Calculate user-specific stats
            long userBytesReceived = userConnections.values().stream().mapToLong(conn -> conn.bytesReceived).sum();
            long userBytesSent = userConnections.values().stream().mapToLong(conn -> conn.bytesSent).sum();
            long userMessages = userConnections.values().stream().mapToLong(conn -> conn.messageCount).sum();
            double userAverageLatency = userConnections.values().stream()
                .mapToDouble(conn -> conn.getAverageLatency())
                .average()
                .orElse(0.0);
            double userThroughput = userConnections.values().stream()
                .mapToDouble(conn -> conn.getThroughput())
                .sum();
            
            String summary = String.format(
                "VNC Performance Summary for User: %s\n" +
                "=====================================\n" +
                "User Connections: %d\n" +
                "Total System Connections: %d\n" +
                "User Bytes Received: %.2f MB\n" +
                "User Bytes Sent: %.2f MB\n" +
                "User Messages: %d\n" +
                "User Average Latency: %.2f ms\n" +
                "User Throughput: %.2f MB/s\n" +
                "Timestamp: %d",
                session.getUserId(),
                userConnections.size(),
                stats.totalConnections,
                userBytesReceived / (1024.0 * 1024.0),
                userBytesSent / (1024.0 * 1024.0),
                userMessages,
                userAverageLatency,
                userThroughput / (1024.0 * 1024.0),
                stats.timestamp
            );
            
            return Response.ok(summary).build();
            
        } catch (Exception e) {
            return Response.serverError()
                .entity("Failed to get performance summary: " + e.getMessage())
                .build();
        }
    }

    private Map<String, VNCConnection> getUserConnections(String sessionId) {
        Map<String, VNCConnection> allConnections = connectionManager.getActiveConnections();
        return allConnections.entrySet().stream()
            .filter(entry -> {
                VNCConnection conn = entry.getValue();
                return conn.authSession != null && sessionId.equals(conn.authSession.getSessionId());
            })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
} 