package com.suko.vnc.rest;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.suko.vnc.websocket.VNCConnection;
import com.suko.vnc.websocket.VNCConnectionManager;
import com.suko.vnc.websocket.VNCPerformanceMonitor;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/vnc/performance")
public class VNCPerformanceResource {
    
    @Inject
    VNCConnectionManager connectionManager;
    
    @Inject
    VNCPerformanceMonitor performanceMonitor;
    
    @ConfigProperty(name = "vnc.server.host", defaultValue = "localhost")
    String vncServerHost;
    
    @ConfigProperty(name = "vnc.server.port", defaultValue = "5901")
    int vncServerPort;
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPerformanceStats() {
        try {
            VNCPerformanceMonitor.PerformanceStats stats = performanceMonitor.getPerformanceStats();
            Map<String, VNCConnection> connections = connectionManager.getActiveConnections();
            
            // Create detailed connection stats
            Map<String, Object> connectionDetails = connections.entrySet().stream()
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
                        connStats.put("throughput", conn.getThroughput());
                        connStats.put("lastActivity", conn.lastActivityTime);
                        connStats.put("clientBufferSize", conn.clientHandler != null ? conn.clientHandler.getBufferSize() : 0);
                        connStats.put("serverBufferSize", conn.serverHandler != null ? conn.serverHandler.getBufferSize() : 0);
                        return connStats;
                    }
                ));
            
            Map<String, Object> response = new HashMap<>();
            response.put("timestamp", stats.timestamp);
            response.put("activeConnections", stats.activeConnections);
            response.put("totalConnections", stats.totalConnections);
            response.put("totalBytesReceived", stats.totalBytesReceived);
            response.put("totalBytesSent", stats.totalBytesSent);
            response.put("totalMessages", stats.totalMessages);
            response.put("averageLatency", stats.averageLatency);
            response.put("totalThroughput", stats.totalThroughput);
            response.put("vncServerHost", vncServerHost);
            response.put("vncServerPort", vncServerPort);
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
    public Response getPerformanceSummary() {
        try {
            VNCPerformanceMonitor.PerformanceStats stats = performanceMonitor.getPerformanceStats();
            
            String summary = String.format(
                "VNC Performance Summary\n" +
                "=======================\n" +
                "Active Connections: %d\n" +
                "Total Connections: %d\n" +
                "Total Bytes Received: %.2f MB\n" +
                "Total Bytes Sent: %.2f MB\n" +
                "Total Messages: %d\n" +
                "Average Latency: %.2f ms\n" +
                "Total Throughput: %.2f MB/s\n" +
                "VNC Server: %s:%d\n" +
                "Timestamp: %d",
                stats.activeConnections,
                stats.totalConnections,
                stats.totalBytesReceived / (1024.0 * 1024.0),
                stats.totalBytesSent / (1024.0 * 1024.0),
                stats.totalMessages,
                stats.averageLatency,
                stats.totalThroughput / (1024.0 * 1024.0),
                vncServerHost,
                vncServerPort,
                stats.timestamp
            );
            
            return Response.ok(summary).build();
            
        } catch (Exception e) {
            return Response.serverError()
                .entity("Failed to get performance summary: " + e.getMessage())
                .build();
        }
    }
} 