package com.suko.vnc.websocket;

import com.suko.vnc.security.VNCAuthService;
import io.quarkus.websockets.next.WebSocketConnection;
import io.vertx.core.net.NetSocket;
import io.vertx.core.buffer.Buffer;

/**
 * Represents a VNC connection with its associated state and data
 */
public class VNCConnection {
    public NetSocket vncSocket;
    public WebSocketConnection webSocketConnection;
    public VNCAuthService.VNCSession authSession;
    public VNCConnectionState state = VNCConnectionState.DISCONNECTED;
    public boolean isConnected = false;
    public long bytesReceived = 0;
    public long bytesSent = 0;
    public long connectionStartTime;
    
    // Performance optimization fields
    public long lastActivityTime;
    public int messageCount = 0;
    public long totalLatency = 0;
    public Buffer clientBuffer = Buffer.buffer();
    public Buffer serverBuffer = Buffer.buffer();
    public static final int BUFFER_THRESHOLD = 1024; // 1KB threshold for buffering
    
    // Latency measurement fields
    public long browserToProxyLatency = 0;
    public long proxyToClientLatency = 0;
    public long proxyToVNCLatency = 0;
    public long lastLatencyUpdate = 0;
    
    // Handshake related fields
    public String serverRfbVersion;
    public String clientRfbVersion;
    public boolean handshakeCompleted = false;
    
    // Security related fields
    public int[] serverSecurityTypes;
    public int selectedSecurityType;
    public boolean securityCompleted = false;
    
    // VNC Authentication fields
    public byte[] vncChallenge;
    public byte[] vncResponse;
    public boolean vncAuthCompleted = false;
    
    // Pending data that needs to be sent when VNC socket becomes available
    public Buffer pendingClientProtocolVersion;
    
    // Handler references to avoid creating new objects for every message
    public VNCClientHandler clientHandler;
    public VNCServerHandler serverHandler;
    public VNCProtocolHandler protocolHandler;
    
    public VNCConnection(VNCAuthService.VNCSession authSession) {
        this.authSession = authSession;
        this.connectionStartTime = System.currentTimeMillis();
        this.lastActivityTime = System.currentTimeMillis();
        this.state = VNCConnectionState.CONNECTING;
    }
    
    public void updateStats(long received, long sent) {
        this.bytesReceived += received;
        this.bytesSent += sent;
        this.lastActivityTime = System.currentTimeMillis();
        this.messageCount++;
    }
    
    public void updateStats(long received, long sent, long latency) {
        this.bytesReceived += received;
        this.bytesSent += sent;
        this.totalLatency += latency;
        this.lastActivityTime = System.currentTimeMillis();
        this.messageCount++;
    }
    
    public void updateLatency(long latency) {
        this.totalLatency += latency;
        this.lastActivityTime = System.currentTimeMillis();
    }
    
    public long getConnectionDuration() {
        return System.currentTimeMillis() - connectionStartTime;
    }
    
    public void setState(VNCConnectionState newState) {
        this.state = newState;
    }
    
    /**
     * Set the WebSocket connection for this VNC connection
     */
    public void setWebSocketConnection(WebSocketConnection webSocketConnection) {
        this.webSocketConnection = webSocketConnection;
    }
    
    /**
     * Set the handlers for this connection
     */
    public void setHandlers(VNCClientHandler clientHandler, VNCServerHandler serverHandler, VNCProtocolHandler protocolHandler) {
        this.clientHandler = clientHandler;
        this.serverHandler = serverHandler;
        this.protocolHandler = protocolHandler;
    }
    
    /**
     * Clean up handler references
     */
    public void cleanup() {
        this.clientHandler = null;
        this.serverHandler = null;
        this.protocolHandler = null;
        this.webSocketConnection = null;
        this.pendingClientProtocolVersion = null;
        this.clientBuffer = null;
        this.serverBuffer = null;
    }
    
    /**
     * Get average latency in milliseconds
     */
    public double getAverageLatency() {
        return messageCount > 0 ? (double) totalLatency / messageCount : 0.0;
    }
    
    /**
     * Set browser-to-proxy latency
     */
    public void setBrowserToProxyLatency(long latency) {
        this.browserToProxyLatency = latency;
    }
    
    /**
     * Get browser-to-proxy latency
     */
    public long getBrowserToProxyLatency() {
        return this.browserToProxyLatency;
    }
    
    /**
     * Set proxy-to-client latency
     */
    public void setProxyToClientLatency(long latency) {
        this.proxyToClientLatency = latency;
    }
    
    /**
     * Get proxy-to-client latency
     */
    public long getProxyToClientLatency() {
        return this.proxyToClientLatency;
    }
    
    /**
     * Set proxy-to-VNC latency
     */
    public void setProxyToVNCLatency(long latency) {
        this.proxyToVNCLatency = latency;
    }
    
    /**
     * Get proxy-to-VNC latency
     */
    public long getProxyToVNCLatency() {
        return this.proxyToVNCLatency;
    }
    
    /**
     * Set last latency update timestamp
     */
    public void setLastLatencyUpdate(long timestamp) {
        this.lastLatencyUpdate = timestamp;
    }
    
    /**
     * Get total end-to-end latency (browser to VNC server)
     */
    public long getTotalEndToEndLatency() {
        return browserToProxyLatency + proxyToClientLatency + proxyToVNCLatency;
    }
    
    /**
     * Get throughput in bytes per second
     */
    public double getThroughput() {
        long duration = getConnectionDuration();
        return duration > 0 ? (double) (bytesReceived + bytesSent) / (duration / 1000.0) : 0.0;
    }
    
    /**
     * Check if connection is idle (no activity for more than 30 seconds)
     */
    public boolean isIdle() {
        return System.currentTimeMillis() - lastActivityTime > 30000;
    }
} 