package com.suko.vnc.websocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Performance monitoring service for VNC proxy
 */
@ApplicationScoped
public class VNCPerformanceMonitor {
    
    private static final Logger log = LoggerFactory.getLogger(VNCPerformanceMonitor.class);
    
    private final Map<String, VNCConnection> connections = new ConcurrentHashMap<>();
    
    // Global performance metrics
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong totalBytesReceived = new AtomicLong(0);
    private final AtomicLong totalBytesSent = new AtomicLong(0);
    private final AtomicLong totalMessages = new AtomicLong(0);
    private final AtomicLong totalLatency = new AtomicLong(0);
    
    /**
     * Register a connection for monitoring
     */
    public void registerConnection(String sessionId, VNCConnection connection) {
        connections.put(sessionId, connection);
        totalConnections.incrementAndGet();
        log.info("Registered connection for monitoring: {}", sessionId);
    }
    
    /**
     * Unregister a connection from monitoring
     */
    public void unregisterConnection(String sessionId) {
        VNCConnection connection = connections.remove(sessionId);
        if (connection != null) {
            // Update global metrics
            totalBytesReceived.addAndGet(connection.bytesReceived);
            totalBytesSent.addAndGet(connection.bytesSent);
            totalMessages.addAndGet(connection.messageCount);
            totalLatency.addAndGet(connection.totalLatency);
            
            log.info("Unregistered connection from monitoring: {}", sessionId);
        }
    }
    
    /**
     * Update live metrics for an active connection
     */
    public void updateLiveMetrics(String sessionId, long bytesReceived, long bytesSent, long messages, long latency) {
        VNCConnection connection = connections.get(sessionId);
        if (connection != null) {
            // Update the connection's metrics
            connection.bytesReceived = bytesReceived;
            connection.bytesSent = bytesSent;
            connection.messageCount = (int) messages;
            connection.totalLatency = latency;
            connection.lastActivityTime = System.currentTimeMillis();
        }
    }
    
    /**
     * Get current performance statistics
     */
    public PerformanceStats getPerformanceStats() {
        long currentTime = System.currentTimeMillis();
        long totalConnectionsCount = totalConnections.get();
        
        // Calculate real-time metrics from active connections
        long totalBytesReceivedCount = 0;
        long totalBytesSentCount = 0;
        long totalMessagesCount = 0;
        long totalLatencyCount = 0;
        
        // Sum up metrics from all active connections
        for (VNCConnection connection : connections.values()) {
            totalBytesReceivedCount += connection.bytesReceived;
            totalBytesSentCount += connection.bytesSent;
            totalMessagesCount += connection.messageCount;
            totalLatencyCount += connection.totalLatency;
        }
        
        // Add historical metrics from closed connections
        totalBytesReceivedCount += this.totalBytesReceived.get();
        totalBytesSentCount += this.totalBytesSent.get();
        totalMessagesCount += this.totalMessages.get();
        totalLatencyCount += this.totalLatency.get();
        
        // Calculate averages
        double avgLatency = totalMessagesCount > 0 ? (double) totalLatencyCount / totalMessagesCount : 0.0;
        double totalThroughput = totalBytesReceivedCount + totalBytesSentCount;
        
        return new PerformanceStats(
            currentTime,
            connections.size(),
            totalConnectionsCount,
            totalBytesReceivedCount,
            totalBytesSentCount,
            totalMessagesCount,
            avgLatency,
            totalThroughput
        );
    }
    
    /**
     * Report performance metrics every 30 seconds using Quarkus scheduler
     */
    @Scheduled(every = "30s")
    void reportPerformance() {
        PerformanceStats stats = getPerformanceStats();
        
        log.info("=== VNC Performance Report ===");
        log.info("Active Connections: {}", stats.activeConnections);
        log.info("Total Connections: {}", stats.totalConnections);
        log.info("Total Bytes Received: {} MB", String.format("%.2f", stats.totalBytesReceived / (1024.0 * 1024.0)));
        log.info("Total Bytes Sent: {} MB", String.format("%.2f", stats.totalBytesSent / (1024.0 * 1024.0)));
        log.info("Total Messages: {}", stats.totalMessages);
        log.info("Average Latency: {} ms", String.format("%.4f", stats.averageLatency));
        log.info("Total Throughput: {} MB/s", String.format("%.2f", stats.totalThroughput / (1024.0 * 1024.0)));
        
        // Report per-connection stats
        if (!connections.isEmpty()) {
            log.info("=== Per-Connection Stats ===");
            connections.forEach((sessionId, connection) -> {
                if (connection.clientHandler != null && connection.serverHandler != null) {
                    // Show a single concise line per connection
                    String shortId = sessionId.substring(0, Math.min(8, sessionId.length()));
                    log.info("Connection {}: Duration={}ms, Received={}MB, Sent={}MB, Messages={}, Avg Latency={}ms, Throughput={}MB/s", 
                            shortId,
                            connection.getConnectionDuration(),
                            String.format("%.2f", connection.bytesReceived / (1024.0 * 1024.0)),
                            String.format("%.2f", connection.bytesSent / (1024.0 * 1024.0)),
                            connection.messageCount,
                            String.format("%.4f", connection.getAverageLatency()),
                            String.format("%.2f", connection.getThroughput() / (1024.0 * 1024.0)));
                }
            });
        }
        
        log.info("===============================");
    }
    
    /**
     * Performance statistics data class
     */
    public static class PerformanceStats {
        public final long timestamp;
        public final int activeConnections;
        public final long totalConnections;
        public final long totalBytesReceived;
        public final long totalBytesSent;
        public final long totalMessages;
        public final double averageLatency;
        public final double totalThroughput;
        
        public PerformanceStats(long timestamp, int activeConnections, long totalConnections,
                              long totalBytesReceived, long totalBytesSent, long totalMessages,
                              double averageLatency, double totalThroughput) {
            this.timestamp = timestamp;
            this.activeConnections = activeConnections;
            this.totalConnections = totalConnections;
            this.totalBytesReceived = totalBytesReceived;
            this.totalBytesSent = totalBytesSent;
            this.totalMessages = totalMessages;
            this.averageLatency = averageLatency;
            this.totalThroughput = totalThroughput;
        }
    }
} 