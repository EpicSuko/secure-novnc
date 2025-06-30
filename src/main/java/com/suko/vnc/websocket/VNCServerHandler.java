package com.suko.vnc.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.Handler;
import io.vertx.mutiny.core.Vertx;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles VNC server communication for VNC proxy with performance optimizations
 */
public class VNCServerHandler {
    
    private static final Logger log = LoggerFactory.getLogger(VNCServerHandler.class);
    
    private final String sessionId;
    private final VNCConnection connection;
    private final String vncServerHost;
    private final int vncServerPort;
    private final String vncServerPassword;
    private final Vertx vertx;
    private final AtomicLong messageId = new AtomicLong(0);
    
    // Store handlers to set them after connection is established
    private Handler<Buffer> serverDataHandler;
    private Handler<Void> serverCloseHandler;
    
    // Performance optimization: batch sending
    private Buffer sendBuffer = Buffer.buffer();
    private long lastSendTime = 0;
    private static final long BATCH_TIMEOUT_MS = 2; // 2ms batch timeout for server (faster than client)
    private static final int MAX_BATCH_SIZE = 16384; // 16KB max batch size for server
    
    // Connection optimization
    private static NetClient sharedNetClient;
    private static final Object clientLock = new Object();
    
    public VNCServerHandler(String sessionId, VNCConnection connection, 
                           String vncServerHost, int vncServerPort, 
                           String vncServerPassword, Vertx vertx) {
        this.sessionId = sessionId;
        this.connection = connection;
        this.vncServerHost = vncServerHost;
        this.vncServerPort = vncServerPort;
        this.vncServerPassword = vncServerPassword;
        this.vertx = vertx;
    }
    
    /**
     * Get or create a shared NetClient for connection pooling
     */
    private NetClient getOrCreateNetClient() {
        if (sharedNetClient == null) {
            synchronized (clientLock) {
                if (sharedNetClient == null) {
                    sharedNetClient = vertx.getDelegate().createNetClient();
                    // Basic configuration for high performance
                    // Note: Advanced socket options are set at the socket level
                }
            }
        }
        return sharedNetClient;
    }
    
    /**
     * Connect to the VNC server with optimized settings
     */
    public void connect(Runnable onSuccess, Runnable onFailure) {
        NetClient netClient = getOrCreateNetClient();
        
        long startTime = System.nanoTime();
        
        netClient.connect(vncServerPort, vncServerHost)
            .onSuccess(vncSocket -> {
                long endTime = System.nanoTime();
                long connectionTime = (endTime - startTime) / 1_000_000; // Convert to milliseconds
                
                connection.vncSocket = vncSocket;
                connection.setState(VNCConnectionState.PROTOCOL_VERSION);
                
                // Configure socket for high performance
                vncSocket.setWriteQueueMaxSize(32768);
                
                log.info("Connected to VNC server: {}:{} for session: {} in {}ms", 
                        vncServerHost, vncServerPort, sessionId, connectionTime);
                
                // Set up handlers after connection is established
                if (serverDataHandler != null) {
                    vncSocket.handler(serverDataHandler);
                }
                
                if (serverCloseHandler != null) {
                    vncSocket.closeHandler(serverCloseHandler);
                }
                
                // Notify protocol handler that VNC socket is ready
                if (connection.protocolHandler != null) {
                    connection.protocolHandler.onVNCSocketReady();
                }
                
                onSuccess.run();
            })
            .onFailure(throwable -> {
                log.error("Failed to connect to VNC server: {}:{} for session: {}", 
                         vncServerHost, vncServerPort, sessionId, throwable);
                onFailure.run();
            });
    }
    
    /**
     * Send data to the VNC server with buffering and batching
     */
    public void sendData(Buffer buffer) {
        if (connection.vncSocket == null) {
            log.warn("Cannot send data to VNC server - socket is null for session: {}", sessionId);
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
        if (sendBuffer.length() > 0 && connection.vncSocket != null) {
            Buffer toSend = sendBuffer.copy();
            int dataSize = toSend.length();
            sendBuffer = Buffer.buffer(); // Reset buffer
            lastSendTime = System.currentTimeMillis();
            
            connection.vncSocket.write(toSend);
            
            long messageIdValue = messageId.incrementAndGet();
            if (log.isDebugEnabled()) {
                log.debug("Sent batch message {} to VNC server for session: {}, size: {} bytes", 
                         messageIdValue, sessionId, dataSize);
            }
            
            // Update stats if requested (for independent flushes like during close)
            if (updateStats) {
                connection.updateStats(0, dataSize);
            }
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
     * Close the VNC server connection
     */
    public void close() {
        // Flush any pending data before closing
        flushSendBuffer(true);
        
        if (connection.vncSocket != null) {
            try {
                connection.vncSocket.close();
                connection.vncSocket = null;
            } catch (Exception e) {
                log.error("Failed to close VNC socket for session: {}", sessionId, e);
            }
        }
    }
    
    /**
     * Check if the VNC server connection is closed
     */
    public boolean isClosed() {
        return connection.vncSocket == null;
    }
    
    /**
     * Get the VNC server password for authentication
     */
    public String getVncServerPassword() {
        return vncServerPassword;
    }
    
    /**
     * Callback when server data is received
     */
    private void onServerDataReceived(Buffer buffer) {
        // This will be overridden by the protocol handler
        log.debug("Server data received: {} bytes for session: {}", buffer.length(), sessionId);
    }
    
    /**
     * Callback when server connection is closed
     */
    private void onServerConnectionClosed() {
        // This will be overridden by the connection manager
        log.debug("Server connection closed for session: {}", sessionId);
    }
    
    /**
     * Set the server data handler
     */
    public void setServerDataHandler(Handler<Buffer> handler) {
        this.serverDataHandler = handler;
        // If socket is already connected, set the handler immediately
        if (connection.vncSocket != null) {
            connection.vncSocket.handler(handler);
        }
    }
    
    /**
     * Set the server close handler
     */
    public void setServerCloseHandler(Handler<Void> handler) {
        this.serverCloseHandler = handler;
        // If socket is already connected, set the handler immediately
        if (connection.vncSocket != null) {
            connection.vncSocket.closeHandler(handler);
        }
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
        return String.format("VNC Server [%s]: Messages: %d, Avg Latency: %.2fms, Throughput: %.2f B/s, Buffer: %d bytes", 
                sessionId, connection.messageCount, connection.getAverageLatency(), 
                connection.getThroughput(), sendBuffer.length());
    }
    
    /**
     * Handle data received from WebSocket client (for stats tracking)
     */
    public void handleReceivedData(Buffer buffer) {
        // Update stats for data received from WebSocket client
        connection.updateStats(0, buffer.length());
    }
} 