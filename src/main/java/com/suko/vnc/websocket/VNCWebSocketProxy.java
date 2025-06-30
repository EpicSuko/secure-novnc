package com.suko.vnc.websocket;

import java.util.Map;

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

    @ConfigProperty(name = "vnc.server.password", defaultValue = "vncpassword")
    String vncServerPassword;

    // Connection manager to handle all VNC connections
    @Inject
    VNCConnectionManager connectionManager;
    
    @OnOpen
    public void onOpen(WebSocketConnection connection, @PathParam String sessionId) {
        log.info("WebSocket connection opened id {} for session: {}", connection.id(), sessionId);

        VNCAuthService.VNCSession vncSession = authService.getSession(sessionId);
            
        if (vncSession == null) {
            log.warn("❌ Invalid or expired session: {}", sessionId);
            connection.closeAndAwait(new CloseReason(WebSocketCloseStatus.ENDPOINT_UNAVAILABLE.code(), "Invalid or expired session"));
            return;
        }
        
        // Create connection and handlers
        VNCConnection vncConnection = connectionManager.createConnection(sessionId, vncSession);
        
        // Store the WebSocket connection for later cleanup
        vncConnection.setWebSocketConnection(connection);
        
        VNCClientHandler clientHandler = new VNCClientHandler(connection, sessionId, vncConnection, vertx);
        VNCServerHandler serverHandler = new VNCServerHandler(sessionId, vncConnection, vncServerHost, vncServerPort, vncServerPassword, vertx);
        VNCProtocolHandler protocolHandler = new VNCProtocolHandler(sessionId, vncConnection, clientHandler, serverHandler);
        
        // Store handlers in the connection for reuse
        vncConnection.setHandlers(clientHandler, serverHandler, protocolHandler);
        
        // Set up server data handler
        serverHandler.setServerDataHandler(buffer -> {
            try {
                protocolHandler.handleServerData(buffer);
            } catch (Exception e) {
                log.error("Error handling server data for session: {}", sessionId, e);
                closeConnection(sessionId, WebSocketCloseStatus.INTERNAL_SERVER_ERROR, "Error handling server data: " + e.getMessage());
            }
        });
        
        // Set up server close handler
        serverHandler.setServerCloseHandler(v -> {
            log.info("VNC server closed connection for session: {}", sessionId);
            closeConnection(sessionId, WebSocketCloseStatus.NORMAL_CLOSURE, "VNC server closed connection");
        });
        
        // Connect to VNC server
        serverHandler.connect(
            () -> log.info("Successfully connected to VNC server for session: {}", sessionId),
            () -> {
                log.error("Failed to connect to VNC server for session: {}", sessionId);
                connection.close(new CloseReason(WebSocketCloseStatus.INTERNAL_SERVER_ERROR.code(), "Failed to connect to VNC server"));
            }
        );
    }

    @OnBinaryMessage
    public void onBinaryMessage(Buffer message, WebSocketConnection connection, @PathParam String sessionId) {
        VNCConnection vncConnection = connectionManager.getConnection(sessionId);
        
        if (vncConnection != null && vncConnection.protocolHandler != null) {
            try {
                log.debug("Processing binary message for session: {}, state: {}, vncSocket: {}, message length: {}", 
                         sessionId, vncConnection.state, vncConnection.vncSocket != null, message.length());
                
                // Check if VNC socket is ready
                if (vncConnection.vncSocket != null) {
                    // VNC socket is ready, process the message
                    vncConnection.protocolHandler.handleClientData(message);
                } else {
                    // VNC socket not ready yet, this might be the first protocol version message
                    // We need to queue it or handle it differently
                    log.debug("VNC socket not ready yet for session: {}, queuing message", sessionId);
                    
                    // For now, let's try to process it anyway - the protocol handler should handle the case
                    // where the server socket is not available
                    vncConnection.protocolHandler.handleClientData(message);
                }
                
            } catch(Exception e) {
                log.error("Failed to process binary message for session: {}", sessionId, e);
                closeConnection(sessionId, WebSocketCloseStatus.INTERNAL_SERVER_ERROR, "Failed to process binary message: " + e.getMessage());
            }
        } else {
            log.warn("No VNC connection or handlers found for session: {}", sessionId);
        }
    }

    @OnTextMessage
    public void onTextMessage(String message, WebSocketConnection connection, @PathParam String sessionId) {
        VNCConnection vncConnection = connectionManager.getConnection(sessionId);

        if (vncConnection != null && vncConnection.isConnected && vncConnection.vncSocket != null && vncConnection.serverHandler != null) {
            try {
                // Reuse existing server handler instead of creating a new one
                vncConnection.serverHandler.sendData(Buffer.buffer(message.getBytes()));
            } catch(Exception e) {
                log.error("Failed to write to VNC socket for session: {}", sessionId, e);
                closeConnection(sessionId, WebSocketCloseStatus.INTERNAL_SERVER_ERROR, "Failed to write to VNC socket: " + e.getMessage());
            }
        }

        connectionManager.logData("WebSocket->VNC", sessionId, Buffer.buffer(message.getBytes()), false);
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
        // Close the connection through the manager (includes WebSocket connection cleanup)
        connectionManager.closeConnection(sessionId, closeStatus, reason);
    }
    
    // Public methods for monitoring
    public Map<String, VNCConnection> getActiveConnections() {
        return connectionManager.getActiveConnections();
    }
    
    public int getActiveConnectionCount() {
        return connectionManager.getActiveConnectionCount();
    }
}
