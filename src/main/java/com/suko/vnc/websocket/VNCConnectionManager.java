package com.suko.vnc.websocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.suko.vnc.security.VNCAuthService;

import io.netty.handler.codec.http.websocketx.WebSocketCloseStatus;
import io.quarkus.websockets.next.CloseReason;
import io.vertx.core.buffer.Buffer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Manages VNC connections and their lifecycle
 */
@ApplicationScoped
public class VNCConnectionManager {
    
    private static final Logger log = LoggerFactory.getLogger(VNCConnectionManager.class);
    
    private final Map<String, VNCConnection> activeConnections = new ConcurrentHashMap<>();
    private final VNCAuthService authService;
    private final VNCPerformanceMonitor performanceMonitor;
    
    @Inject
    public VNCConnectionManager(VNCAuthService authService, VNCPerformanceMonitor performanceMonitor) {
        this.authService = authService;
        this.performanceMonitor = performanceMonitor;
    }
    
    /**
     * Create a new VNC connection
     */
    public VNCConnection createConnection(String sessionId, VNCAuthService.VNCSession authSession) {
        VNCConnection connection = new VNCConnection(authSession);
        activeConnections.put(sessionId, connection);
        
        // Register with performance monitor
        performanceMonitor.registerConnection(sessionId, connection);
        
        log.info("Created VNC connection for session: {}", sessionId);
        return connection;
    }
    
    /**
     * Get an existing VNC connection
     */
    public VNCConnection getConnection(String sessionId) {
        return activeConnections.get(sessionId);
    }
    
    /**
     * Remove and close a VNC connection
     */
    public void closeConnection(String sessionId, WebSocketCloseStatus closeStatus, String reason) {
        VNCConnection vncConnection = activeConnections.remove(sessionId);
        if (vncConnection != null) {
            // Set state to disconnected
            vncConnection.setState(VNCConnectionState.DISCONNECTED);
            
            // Close the VNC connection
            vncConnection.isConnected = false;
            if(vncConnection.vncSocket != null) {
                try {
                    vncConnection.vncSocket.close();
                    vncConnection.vncSocket = null;
                } catch(Exception e) {
                    log.error("Failed to close VNC socket for session: {}", sessionId, e);
                }
            }

            // Close the WebSocket connection if it's still open
            if(vncConnection.webSocketConnection != null && !vncConnection.webSocketConnection.isClosed()) {
                try {
                    vncConnection.webSocketConnection.close(new CloseReason(closeStatus.code(), reason));
                    log.info("Closed WebSocket connection for session: {}", sessionId);
                } catch(Exception e) {
                    log.error("Failed to close WebSocket connection for session: {}", sessionId, e);
                }
            }

            // Clean up handler references
            vncConnection.cleanup();

            // Update session activity
            if(vncConnection.authSession != null) {
                authService.getSession(sessionId);
            }

            // Unregister from performance monitor
            performanceMonitor.unregisterConnection(sessionId);

            long duration = vncConnection.getConnectionDuration();
            log.info("Connection stats for session {}: Duration={}ms, Received={}bytes, Sent={}bytes, Avg Latency={}ms, Throughput={} B/s", 
                    sessionId, duration, vncConnection.bytesReceived, vncConnection.bytesSent, 
                    String.format("%.2f", vncConnection.getAverageLatency()), 
                    String.format("%.2f", vncConnection.getThroughput()));
        }
    }
    
    /**
     * Get all active connections (read-only copy)
     */
    public Map<String, VNCConnection> getActiveConnections() {
        return Map.copyOf(activeConnections);
    }
    
    /**
     * Get the number of active connections
     */
    public int getActiveConnectionCount() {
        return activeConnections.size();
    }
    
    /**
     * Check if a connection exists for the given session
     */
    public boolean hasConnection(String sessionId) {
        return activeConnections.containsKey(sessionId);
    }
    
    /**
     * Log data for debugging purposes
     */
    public void logData(String direction, String sessionId, Buffer buffer, boolean logOverride) {
        if (!logOverride) {
            return;
        }

        try {
            byte[] data = buffer.getBytes();
            String hexDump = bytesToHex(data);
            String ascii = bytesToAscii(data);
            
            log.info("{} [{}] {} bytes", direction, sessionId, data.length);
            log.info("{} [{}] Hex: {}", direction, sessionId, hexDump);
            log.info("{} [{}] ASCII: '{}'", direction, sessionId, ascii);
            
            // If it looks like text, also log it as string
            if (isReadableText(data)) {
                String text = buffer.toString();
                log.info("{} [{}] Text: '{}'", direction, sessionId, text);
            }
            
        } catch (Exception e) {
            log.warn("Error logging data: {}", e.getMessage());
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x ", b));
        }
        return hex.toString().trim();
    }
    
    private String bytesToAscii(byte[] bytes) {
        StringBuilder ascii = new StringBuilder();
        for (byte b : bytes) {
            if (b >= 32 && b <= 126) { // Printable ASCII
                ascii.append((char) b);
            } else {
                ascii.append('.');
            }
        }
        return ascii.toString();
    }
    
    private boolean isReadableText(byte[] bytes) {
        int printableCount = 0;
        for (byte b : bytes) {
            if (b >= 32 && b <= 126) { // Printable ASCII
                printableCount++;
            }
        }
        // Consider it text if more than 70% is printable ASCII
        return bytes.length > 0 && (printableCount * 100 / bytes.length) > 70;
    }
} 