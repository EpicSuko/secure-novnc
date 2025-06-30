package com.suko.vnc.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.websockets.next.WebSocketConnection;
import io.quarkus.websockets.next.CloseReason;
import io.vertx.core.buffer.Buffer;
import io.vertx.mutiny.core.Vertx;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles WebSocket client communication for VNC proxy with performance optimizations
 */
public class VNCClientHandler {
    
    private static final Logger log = LoggerFactory.getLogger(VNCClientHandler.class);
    
    private final WebSocketConnection webSocketConnection;
    private final String sessionId;
    private final VNCConnection connection;
    private final AtomicLong messageId = new AtomicLong(0);
    private final Vertx vertx;
    
    // Performance optimization: batch sending
    private Buffer sendBuffer = Buffer.buffer();
    private long lastSendTime = 0;
    private static final long BATCH_TIMEOUT_MS = 5; // 5ms batch timeout
    private static final int MAX_BATCH_SIZE = 8192; // 8KB max batch size
    
    // Timer for periodic flushing when client is idle
    private Long flushTimerId = null;
    private static final long FLUSH_INTERVAL_MS = 10; // 10ms periodic flush interval
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    
    public VNCClientHandler(WebSocketConnection webSocketConnection, String sessionId, VNCConnection connection, Vertx vertx) {
        this.webSocketConnection = webSocketConnection;
        this.sessionId = sessionId;
        this.connection = connection;
        this.vertx = vertx;
        
        // Start periodic flush timer
        startFlushTimer();
    }
    
    /**
     * Start the periodic flush timer
     */
    private void startFlushTimer() {
        if (flushTimerId == null) {
            flushTimerId = vertx.setPeriodic(FLUSH_INTERVAL_MS, timerId -> {
                if (!isClosed.get() && sendBuffer.length() > 0) {
                    // Check if enough time has passed since last send
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastSendTime >= BATCH_TIMEOUT_MS) {
                        if (log.isDebugEnabled()) {
                            log.debug("Periodic flush triggered for session: {}, buffer size: {} bytes", 
                                    sessionId, sendBuffer.length());
                        }
                        flushSendBuffer();
                    }
                }
            });
        }
    }
    
    /**
     * Stop the periodic flush timer
     */
    private void stopFlushTimer() {
        if (flushTimerId != null) {
            vertx.cancelTimer(flushTimerId);
            flushTimerId = null;
        }
    }
    
    /**
     * Send binary data to the WebSocket client with buffering and batching
     */
    public void sendBinary(Buffer buffer) {
        if (webSocketConnection == null || webSocketConnection.isClosed() || isClosed.get()) {
            return;
        }
        
        long startTime = System.nanoTime();
        
        // Add to send buffer
        sendBuffer.appendBuffer(buffer);
        
        // Check if we should send immediately
        boolean shouldSend = shouldSendNow(buffer.length());
        
        if (shouldSend) {
            int totalDataSize = sendBuffer.length();
            flushSendBuffer();
            long endTime = System.nanoTime();
            long latency = (endTime - startTime) / 1_000_000; // Convert to milliseconds
            // Update stats only when data is actually sent
            connection.updateStats(0, totalDataSize, latency);
        }
        // Don't update stats here - only when data is actually sent
    }
    
    /**
     * Send text data to the WebSocket client (immediate send for text)
     */
    public void sendText(String text) {
        if (webSocketConnection == null || webSocketConnection.isClosed() || isClosed.get()) {
            return;
        }
        
        long startTime = System.nanoTime();
        
        webSocketConnection.sendText(text).subscribe().with(
            success -> {
                long endTime = System.nanoTime();
                long latency = (endTime - startTime) / 1_000_000;
                connection.updateStats(text.length(), 0, latency);
            },
            failure -> log.error("Failed to send text data to client for session: {}", sessionId, failure)
        );
    }
    
    /**
     * Flush any pending data in the send buffer
     */
    public void flushSendBuffer() {
        flushSendBuffer(false);
    }
    
    /**
     * Flush any pending data in the send buffer
     * @param updateStats whether to update stats for this flush operation
     */
    public void flushSendBuffer(boolean updateStats) {
        if (sendBuffer.length() > 0) {
            Buffer toSend = sendBuffer.copy();
            int dataSize = toSend.length();
            sendBuffer = Buffer.buffer(); // Reset buffer
            
            // Update lastSendTime when we actually send data
            lastSendTime = System.currentTimeMillis();
            
            webSocketConnection.sendBinary(toSend).subscribe().with(
                success -> {
                    long messageIdValue = messageId.incrementAndGet();
                    if (log.isDebugEnabled()) {
                        log.debug("Sent batch message {} to client for session: {}, size: {} bytes", 
                                messageIdValue, sessionId, dataSize);
                    }
                    
                    // Update stats if requested (for independent flushes like during close)
                    if (updateStats) {
                        connection.updateStats(0, dataSize);
                    }
                },
                failure -> log.error("Failed to send binary data to client for session: {}", sessionId, failure)
            );
        }
    }
    
    /**
     * Determine if we should send the buffer now based on size and timing
     */
    private boolean shouldSendNow(int newDataSize) {
        long currentTime = System.currentTimeMillis();
        
        // Send immediately if:
        // 1. Buffer is getting too large
        if (sendBuffer.length() + newDataSize >= MAX_BATCH_SIZE) {
            return true;
        }
        
        // 2. Enough time has passed since last send
        if (currentTime - lastSendTime >= BATCH_TIMEOUT_MS) {
            return true;
        }
        
        // 3. This is a large message (send immediately)
        if (newDataSize >= VNCConnection.BUFFER_THRESHOLD) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Close the WebSocket connection
     */
    public void close(int code, String reason) {
        // Mark as closed to prevent new operations
        isClosed.set(true);
        
        // Stop the flush timer
        stopFlushTimer();
        
        // Flush any pending data before closing
        flushSendBuffer(true);
        
        if (webSocketConnection != null && !webSocketConnection.isClosed()) {
            try {
                webSocketConnection.close(new CloseReason(code, reason));
            } catch (Exception e) {
                log.error("Failed to close WebSocket connection for session: {}", sessionId, e);
            }
        }
    }
    
    /**
     * Check if the WebSocket connection is closed
     */
    public boolean isClosed() {
        return isClosed.get() || webSocketConnection == null || webSocketConnection.isClosed();
    }
    
    /**
     * Get the WebSocket connection ID
     */
    public String getConnectionId() {
        return webSocketConnection != null ? webSocketConnection.id() : "unknown";
    }
    
    /**
     * Get current buffer size
     */
    public int getBufferSize() {
        return sendBuffer.length();
    }
    
    /**
     * Get performance statistics
     */
    public String getPerformanceStats() {
        return String.format("Session: %s, Messages: %d, Avg Latency: %.2fms, Throughput: %.2f B/s, Buffer: %d bytes", 
                sessionId, connection.messageCount, connection.getAverageLatency(), 
                connection.getThroughput(), sendBuffer.length());
    }
    
    /**
     * Log data being sent to client (for debugging)
     */
    public void logData(String direction, Buffer buffer, boolean logOverride) {
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
    
    /**
     * Handle data received from VNC server (for stats tracking)
     */
    public void handleReceivedData(Buffer buffer) {
        // Update stats for data received from VNC server
        connection.updateStats(buffer.length(), 0);
    }
} 