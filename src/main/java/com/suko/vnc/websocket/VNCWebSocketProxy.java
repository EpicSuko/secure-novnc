package com.suko.vnc.websocket;

import java.util.Map;
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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import io.vertx.mutiny.core.Vertx;
import jakarta.inject.Inject;

@WebSocket(path = "/websockify/{sessionId}")
public class VNCWebSocketProxy {
    
    private static final Logger log = LoggerFactory.getLogger(VNCWebSocketProxy.class);
    
    @Inject
    VNCAuthService authService;
    
    @Inject
    Vertx vertx;
    
    @ConfigProperty(name = "vnc.server.host", defaultValue = "localhost")
    String vncServerHost;
    
    @ConfigProperty(name = "vnc.server.port", defaultValue = "5901")
    int vncServerPort;

    // Store active WebSocket connections with their VNC sockets
    private final Map<String, VNCConnection> activeConnections = new ConcurrentHashMap<>();
    
    public static class VNCConnection {
        public WebSocketConnection webSocketConnection;
        public NetSocket vncSocket;
        public VNCAuthService.VNCSession authSession;
        public boolean isConnected = false;
        public long bytesReceived = 0;
        public long bytesSent = 0;
        public long connectionStartTime;
        
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
    public void onOpen(WebSocketConnection connection, @PathParam String sessionId) {
        log.info("WebSocket connection opened id {} for session: {}", connection.id(), sessionId);

        VNCAuthService.VNCSession vncSession = authService.getSession(sessionId);
            
        if (vncSession == null) {
            log.warn("❌ Invalid or expired session: {}", sessionId);
            connection.closeAndAwait(new CloseReason(WebSocketCloseStatus.ENDPOINT_UNAVAILABLE.code(), "Invalid or expired session"));
            return;
        }
        
        NetClient netClient = vertx.getDelegate().createNetClient();

        netClient.connect(vncServerPort, vncServerHost)
            .onSuccess(vncSocket -> {
                VNCConnection vncConnection = new VNCConnection(connection, vncSession);
                vncConnection.vncSocket = vncSocket;
                vncConnection.isConnected = true;    
                activeConnections.put(sessionId, vncConnection);

                log.info("Connected to VNC server: {}:{}", vncServerHost, vncServerPort);

                vncSocket.handler(buffer -> {
                    connection.sendBinary(buffer).subscribe().with(s -> { 
                        vncConnection.updateStats(buffer.length(), 0);
                    });
                    logData("VNC->WebSocket", sessionId, buffer);
                });

                vncSocket.closeHandler(h -> {
                    log.info("VNC server closed connection for session: {}", sessionId);
                    closeConnection(sessionId, WebSocketCloseStatus.NORMAL_CLOSURE, "VNC server closed connection");
                });

            })
            .onFailure(throwable -> {
                connection.close(new CloseReason(WebSocketCloseStatus.INTERNAL_SERVER_ERROR.code(), "Failed to connect to VNC server: " + throwable.getMessage()));
                log.error("Failed to connect to VNC server: {}:{}", vncServerHost, vncServerPort, throwable);
            });
    }

    @OnBinaryMessage
    public void onBinaryMessage(Buffer message, WebSocketConnection connection, @PathParam String sessionId) {
        VNCConnection vncConnection = activeConnections.get(sessionId);

        if (vncConnection != null && vncConnection.isConnected && vncConnection.vncSocket != null) {
            try {
                vncConnection.vncSocket.write(message);
                vncConnection.updateStats(0, message.length());
            } catch(Exception e) {
                log.error("Failed to write to VNC socket", e);
                closeConnection(sessionId, WebSocketCloseStatus.INTERNAL_SERVER_ERROR, "Failed to write to VNC socket: " + e.getMessage());
            }
        }

        logData("WebSocket->VNC", sessionId, message);
    }

    @OnTextMessage
    public void onTextMessage(String message, WebSocketConnection connection, @PathParam String sessionId) {
        VNCConnection vncConnection = activeConnections.get(sessionId);

        if (vncConnection != null && vncConnection.isConnected && vncConnection.vncSocket != null) {
            try {
                vncConnection.vncSocket.write(Buffer.buffer(message.getBytes()));
                vncConnection.updateStats(0, message.length());
            } catch(Exception e) {
                log.error("Failed to write to VNC socket", e);
                closeConnection(sessionId, WebSocketCloseStatus.INTERNAL_SERVER_ERROR, "Failed to write to VNC socket: " + e.getMessage());
            }
        }

        logData("WebSocket->VNC", sessionId, Buffer.buffer(message.getBytes()));
    }

    @OnClose
    public void onClose(WebSocketConnection connection, @PathParam String sessionId) {
        log.info("WebSocket connection closed for session: {}", sessionId);
        closeConnection(sessionId, WebSocketCloseStatus.NORMAL_CLOSURE, "WebSocket connection closed");
    }

    @OnError
    public void onError(WebSocketConnection connection, Throwable throwable, @PathParam String sessionId) {
        log.error("WebSocket error for session: {}", sessionId, throwable);
        closeConnection(sessionId, WebSocketCloseStatus.INTERNAL_SERVER_ERROR, "WebSocket error: " + throwable.getMessage());
    }
    
    /**
     * Clean up and close a VNC connection for the given session
     * @param sessionId the session ID to close
     * @param closeStatus the WebSocket close status to use
     * @param reason the reason for closing
     */
    private void closeConnection(String sessionId, WebSocketCloseStatus closeStatus, String reason) {
        VNCConnection vncConnection = activeConnections.remove(sessionId);
        if (vncConnection != null) {
            // Close the VNC connection
            vncConnection.isConnected = false;
            if(vncConnection.vncSocket != null) {
                try {
                    vncConnection.vncSocket.close();
                    vncConnection.vncSocket = null;
                } catch(Exception e) {
                    log.error("Failed to close VNC socket", e);
                }
            }

            // Close the WebSocket connection if it's still open
            if(vncConnection.webSocketConnection != null) {
                try {
                    vncConnection.webSocketConnection.close(new CloseReason(closeStatus.code(), reason));
                    vncConnection.webSocketConnection = null;
                } catch(Exception e) {
                    log.error("Failed to close WebSocket connection", e);
                }
            }

            // Update session activity
            if(vncConnection.authSession != null) {
                authService.getSession(sessionId);
            }

            long duration = vncConnection.getConnectionDuration();
            log.info("Connection stats for session {}: Duration={}ms, Received={}bytes, Sent={}bytes", sessionId, duration, vncConnection.bytesReceived, vncConnection.bytesSent);
        }
    }
    
    // Public methods for monitoring
    public Map<String, VNCConnection> getActiveConnections() {
        return Map.copyOf(activeConnections);
    }
    
    public int getActiveConnectionCount() {
        return activeConnections.size();
    }
    
    private void logData(String direction, String sessionId, Buffer buffer) {

        boolean shouldLog = false;
        if(!shouldLog) {
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
