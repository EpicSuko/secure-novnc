package com.suko.vnc.websocket;

import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.suko.vnc.security.VNCAuthService;

import io.netty.handler.codec.http.websocketx.WebSocketCloseStatus;
import io.quarkus.websockets.next.CloseReason;
import io.quarkus.websockets.next.OnBinaryMessage;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnError;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.PathParam;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Uni;
import io.vertx.core.buffer.Buffer;
import jakarta.inject.Inject;

@WebSocket(path = "/websockify/{sessionId}")
public class VNCWebSocketProxy {
    
    private static final Logger log = LoggerFactory.getLogger(VNCWebSocketProxy.class);
    
    @Inject
    VNCAuthService authService;
    
    @ConfigProperty(name = "vnc.server.host", defaultValue = "localhost")
    String vncServerHost;
    
    @ConfigProperty(name = "vnc.server.port", defaultValue = "5901")
    int vncServerPort;
    
    // Store active WebSocket connections with their VNC sockets
    private final Map<String, VNCConnection> activeConnections = new ConcurrentHashMap<>();
    
    public static class VNCConnection {
        public WebSocketConnection webSocketConnection;
        public Socket vncSocket;
        public VNCAuthService.VNCSession authSession;
        public boolean isConnected = false;
        public long bytesReceived = 0;
        public long bytesSent = 0;
        public long connectionStartTime;
        public CompletableFuture<Void> forwardingTask;
        
        public VNCConnection(WebSocketConnection webSocketConnection, VNCAuthService.VNCSession authSession) {
            this.webSocketConnection = webSocketConnection;
            this.authSession = authSession;
            this.connectionStartTime = System.currentTimeMillis();
        }
        
        public void updateStats(long received, long sent) {
            this.bytesReceived += received;
            this.bytesSent += sent;
        }
        
        public long getConnectionDuration() {
            return System.currentTimeMillis() - connectionStartTime;
        }
    }
    
    @OnOpen
    public Uni<Void> onOpen(WebSocketConnection connection, @PathParam String sessionId) {
        return Uni.createFrom().item(() -> {
            log.info("🔗 WebSocket connection opened for session: {}", sessionId);
            
            // Validate session first
            VNCAuthService.VNCSession vncSession = authService.getSession(sessionId);
            
            if (vncSession == null) {
                log.warn("❌ Invalid or expired session: {}", sessionId);
                connection.closeAndAwait(new CloseReason(WebSocketCloseStatus.ENDPOINT_UNAVAILABLE.code(), "Invalid or expired session"));
                return null;
            }
            
            try {
                // Create connection to actual VNC server
                log.info("🖥️ Connecting to VNC server: {}:{}", vncServerHost, vncServerPort);
                Socket vncSocket = new Socket(vncServerHost, vncServerPort);
                vncSocket.setTcpNoDelay(true); // Important for VNC performance
                vncSocket.setKeepAlive(true);
                vncSocket.setSoTimeout(30000); // 30 second timeout
                
                VNCConnection vncConnection = new VNCConnection(connection, vncSession);
                vncConnection.vncSocket = vncSocket;
                vncConnection.isConnected = true;
                
                activeConnections.put(sessionId, vncConnection);
                
                // Start bidirectional data forwarding
                startDataForwarding(sessionId, vncConnection);
                
                log.info("✅ VNC proxy connection established for user: {} ({})", 
                    vncSession.userId, sessionId);
                
            } catch (IOException e) {
                log.error("❌ Failed to connect to VNC server {}:{}", vncServerHost, vncServerPort, e);
                connection.closeAndAwait(new CloseReason(WebSocketCloseStatus.INTERNAL_SERVER_ERROR.code(), "Failed to connect to VNC server: " + e.getMessage()));
            }
            
            return null;
        });
    }
    
    @OnBinaryMessage
    public Uni<Void> onBinaryMessage(Buffer message, WebSocketConnection connection, @PathParam String sessionId) {
        return Uni.createFrom().item(() -> {
            VNCConnection vncConnection = activeConnections.get(sessionId);
            
            if (vncConnection != null && vncConnection.isConnected && vncConnection.vncSocket != null) {
                try {
                    // Forward binary data from WebSocket client to VNC server
                    byte[] bytes = message.getBytes();
                    vncConnection.vncSocket.getOutputStream().write(bytes);
                    vncConnection.vncSocket.getOutputStream().flush();
                    
                    vncConnection.updateStats(0, bytes.length);
                    log.trace("📥 Forwarded {} bytes WebSocket->VNC", bytes.length);
                    
                } catch (IOException e) {
                    log.warn("❌ Error forwarding WebSocket->VNC data for session {}: {}", 
                        sessionId, e.getMessage());
                    closeConnection(sessionId);
                }
            } else {
                log.warn("⚠️ Received message for inactive connection: {}", sessionId);
            }
            
            return null;
        });
    }
    
    @OnTextMessage
    public Uni<Void> onTextMessage(String message, WebSocketConnection connection, @PathParam String sessionId) {
        return Uni.createFrom().item(() -> {
            // Text messages typically not used in VNC protocol, but log for debugging
            log.debug("📨 Received text message for session {}: {}", sessionId, message);
            return null;
        });
    }
    
    @OnClose
    public Uni<Void> onClose(WebSocketConnection connection, @PathParam String sessionId) {
        return Uni.createFrom().item(() -> {
            log.info("🔌 WebSocket connection closed for session: {}", sessionId);
            closeConnection(sessionId);
            return null;
        });
    }
    
    @OnError
    public Uni<Void> onError(WebSocketConnection connection, Throwable throwable, @PathParam String sessionId) {
        return Uni.createFrom().item(() -> {
            log.error("💥 WebSocket error for session {}: {}", sessionId, throwable.getMessage(), throwable);
            closeConnection(sessionId);
            return null;
        });
    }
    
    private void startDataForwarding(String sessionId, VNCConnection vncConnection) {
        // Forward data from VNC server to WebSocket client using reactive approach
        vncConnection.forwardingTask = CompletableFuture.runAsync(() -> {
            log.debug("🔄 Starting VNC->WebSocket forwarding for session: {}", sessionId);
            byte[] buffer = new byte[8192]; // Larger buffer for better performance
            
            try {
                while (vncConnection.isConnected && 
                       vncConnection.vncSocket != null &&
                       !vncConnection.vncSocket.isClosed() && 
                       vncConnection.webSocketConnection.isOpen()) {
                    
                    int bytesRead = vncConnection.vncSocket.getInputStream().read(buffer);
                    if (bytesRead > 0) {
                        // Forward to WebSocket client using WebSockets Next API
                        byte[] data = new byte[bytesRead];
                        System.arraycopy(buffer, 0, data, 0, bytesRead);
                        
                        Buffer vertxBuffer = Buffer.buffer(data);
                        vncConnection.webSocketConnection.sendBinaryAndAwait(vertxBuffer);
                        
                        vncConnection.updateStats(bytesRead, 0);
                        log.trace("📤 Forwarded {} bytes VNC->WebSocket", bytesRead);
                        
                    } else if (bytesRead == -1) {
                        log.info("📡 VNC server closed connection for session: {}", sessionId);
                        break;
                    }
                }
            } catch (IOException e) {
                if (vncConnection.isConnected) {
                    log.warn("❌ Error forwarding VNC->WebSocket data for session {}: {}", 
                        sessionId, e.getMessage());
                }
            } catch (Exception e) {
                log.error("💥 Unexpected error in data forwarding for session {}: {}", 
                    sessionId, e.getMessage(), e);
            } finally {
                closeConnection(sessionId);
            }
        });
    }
    
    private void closeConnection(String sessionId) {
        VNCConnection vncConnection = activeConnections.remove(sessionId);
        if (vncConnection != null) {
            vncConnection.isConnected = false;
            
            // Cancel forwarding task
            if (vncConnection.forwardingTask != null && !vncConnection.forwardingTask.isDone()) {
                vncConnection.forwardingTask.cancel(true);
            }
            
            try {
                if (vncConnection.vncSocket != null && !vncConnection.vncSocket.isClosed()) {
                    vncConnection.vncSocket.close();
                    log.debug("🔌 Closed VNC socket for session: {}", sessionId);
                }
            } catch (IOException e) {
                log.warn("Error closing VNC socket for session {}: {}", sessionId, e.getMessage());
            }
            
            try {
                if (vncConnection.webSocketConnection != null && vncConnection.webSocketConnection.isOpen()) {
                    vncConnection.webSocketConnection.closeAndAwait();
                    log.debug("🔌 Closed WebSocket for session: {}", sessionId);
                }
            } catch (Exception e) {
                log.warn("Error closing WebSocket for session {}: {}", sessionId, e.getMessage());
            }
            
            // Log connection statistics
            long duration = vncConnection.getConnectionDuration();
            log.info("📊 Connection stats for session {}: Duration={}ms, Received={}bytes, Sent={}bytes", 
                sessionId, duration, vncConnection.bytesReceived, vncConnection.bytesSent);
            
            // Update session activity
            if (vncConnection.authSession != null) {
                authService.getSession(sessionId); // This updates last activity
            }
        }
    }
    
    // Public methods for monitoring
    public Map<String, VNCConnection> getActiveConnections() {
        return Map.copyOf(activeConnections);
    }
    
    public int getActiveConnectionCount() {
        return activeConnections.size();
    }
}
