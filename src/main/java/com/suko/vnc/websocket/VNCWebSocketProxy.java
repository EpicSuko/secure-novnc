package com.suko.vnc.websocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Cipher;
import javax.crypto.spec.DESKeySpec;
import javax.crypto.SecretKeyFactory;

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

    @ConfigProperty(name = "vnc.server.password", defaultValue = "vncpassword")
    String vncServerPassword;

    // Store active WebSocket connections with their VNC sockets
    private final Map<String, VNCConnection> activeConnections = new ConcurrentHashMap<>();
    
    public static class VNCConnection {
        public WebSocketConnection webSocketConnection;
        public NetSocket vncSocket;
        public VNCAuthService.VNCSession authSession;
        public VNCConnectionState state = VNCConnectionState.DISCONNECTED;
        public boolean isConnected = false;
        public long bytesReceived = 0;
        public long bytesSent = 0;
        public long connectionStartTime;
        
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
        
        public VNCConnection(WebSocketConnection webSocketConnection, VNCAuthService.VNCSession authSession) {
            this.webSocketConnection = webSocketConnection;
            this.authSession = authSession;
            this.connectionStartTime = System.currentTimeMillis();
            this.state = VNCConnectionState.CONNECTING;
        }
        
        public void updateStats(long received, long sent) {
            this.bytesReceived += received;
            this.bytesSent += sent;
        }
        
        public long getConnectionDuration() {
            return System.currentTimeMillis() - connectionStartTime;
        }
        
        public void setState(VNCConnectionState newState) {
            this.state = newState;
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
                vncConnection.setState(VNCConnectionState.PROTOCOL_VERSION);
                activeConnections.put(sessionId, vncConnection);

                log.info("Connected to VNC server: {}:{}", vncServerHost, vncServerPort);

                vncSocket.handler(buffer -> {
                    handleVNCServerData(sessionId, buffer);
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

    /**
     * Handle data received from the VNC server
     */
    private void handleVNCServerData(String sessionId, Buffer buffer) {
        VNCConnection vncConnection = activeConnections.get(sessionId);
        if (vncConnection == null) {
            log.warn("No VNC connection found for session: {}", sessionId);
            return;
        }

        switch (vncConnection.state) {
            case PROTOCOL_VERSION:
                handleProtocolVersion(sessionId, buffer);
                break;
            case SECURITY:
                handleSecurity(sessionId, buffer);
                break;
            case VNC_AUTH:
                handleVNCAuth(sessionId, buffer);
                break;
            case AUTH:
                handleAuth(sessionId, buffer);
                break;
            case CONNECTED:
                // Forward data to WebSocket client
                vncConnection.webSocketConnection.sendBinary(buffer).subscribe().with(s -> { 
                    vncConnection.updateStats(buffer.length(), 0);
                });
                break;
            default:
                log.warn("Received data in unexpected state: {} for session: {}", vncConnection.state, sessionId);
                break;
        }
    }

    /**
     * Handle the RFB protocol version exchange with the VNC server
     */
    private void handleProtocolVersion(String sessionId, Buffer buffer) {
        VNCConnection vncConnection = activeConnections.get(sessionId);
        if (vncConnection == null) {
            return;
        }

        try {
            // RFB version string is 12 bytes
            if (buffer.length() >= 12) {
                String serverVersion = buffer.getString(0, 12);
                vncConnection.serverRfbVersion = serverVersion;
                
                log.info("VNC server version: {} for session: {}", serverVersion, sessionId);
                
                // Forward the server version to the WebSocket client
                vncConnection.webSocketConnection.sendBinary(buffer).subscribe().with(s -> {
                    vncConnection.updateStats(0, 0);
                });
                
                log.info("Protocol version forwarded to client for session: {}", sessionId);
                
                // Stay in PROTOCOL_VERSION state - wait for client to send their version
                
            } else {
                log.warn("Incomplete protocol version data received: {} bytes for session: {}", buffer.length(), sessionId);
            }
        } catch (Exception e) {
            log.error("Error during protocol version exchange for session: {}", sessionId, e);
            closeConnection(sessionId, WebSocketCloseStatus.INTERNAL_SERVER_ERROR, "Protocol version exchange failed: " + e.getMessage());
        }
    }

    /**
     * Handle the security types exchange with the VNC server
     */
    private void handleSecurity(String sessionId, Buffer buffer) {
        VNCConnection vncConnection = activeConnections.get(sessionId);
        if (vncConnection == null) {
            return;
        }

        try {
            // The server sends: [number-of-security-types][security-types]
            // number-of-security-types is 1 byte (U8)
            if (buffer.length() >= 1) {
                int numSecurityTypes = buffer.getUnsignedByte(0);
                log.info("Server offers {} security types for session: {}", numSecurityTypes, sessionId);
                
                if (numSecurityTypes == 0) {
                    log.warn("Server offers no security types for session: {}", sessionId);
                    return;
                }
                
                if (buffer.length() >= 1 + numSecurityTypes) {
                    // Read the security types
                    int[] securityTypes = new int[numSecurityTypes];
                    for (int i = 0; i < numSecurityTypes; i++) {
                        securityTypes[i] = buffer.getUnsignedByte(1 + i);
                    }
                    vncConnection.serverSecurityTypes = securityTypes;
                    
                    // Log available security types
                    StringBuilder securityTypesStr = new StringBuilder();
                    for (int i = 0; i < securityTypes.length; i++) {
                        if (i > 0) securityTypesStr.append(", ");
                        securityTypesStr.append(getSecurityTypeName(securityTypes[i]));
                    }
                    log.info("Available security types: {} for session: {}", securityTypesStr.toString(), sessionId);
                    
                    // Check if VNC Authentication (type 2) is available
                    boolean vncAuthAvailable = false;
                    boolean noneAuthAvailable = false;
                    for (int type : securityTypes) {
                        if (type == 2) vncAuthAvailable = true;
                        if (type == 1) noneAuthAvailable = true;
                    }
                    
                    if (vncAuthAvailable) {
                        // Send None authentication to client, but we'll handle VNC auth with server
                        Buffer noneAuthToClient = Buffer.buffer(2);
                        noneAuthToClient.setByte(0, (byte) 1); // 1 security type
                        noneAuthToClient.setByte(1, (byte) 1); // None authentication
                        
                        log.info("Secure mode: Sending None auth to client, will handle VNC auth with server for session: {}", sessionId);
                        
                        // Forward None authentication to the WebSocket client
                        vncConnection.webSocketConnection.sendBinary(noneAuthToClient).subscribe().with(s -> {
                            vncConnection.updateStats(0, 0);
                        });
                        
                        // Store that we need to handle VNC auth with server
                        vncConnection.selectedSecurityType = 2; // VNC Authentication for server
                    } else if (noneAuthAvailable) {
                        // Only None auth available, send it to both client and server
                        Buffer noneAuthOnly = Buffer.buffer(2);
                        noneAuthOnly.setByte(0, (byte) 1); // 1 security type
                        noneAuthOnly.setByte(1, (byte) 1); // None authentication
                        
                        log.info("Only None auth available, sending None authentication for session: {}", sessionId);
                        
                        // Forward only None authentication to the WebSocket client
                        vncConnection.webSocketConnection.sendBinary(noneAuthOnly).subscribe().with(s -> {
                            vncConnection.updateStats(0, 0);
                        });
                        
                        vncConnection.selectedSecurityType = 1; // None authentication
                    } else {
                        // If no preferred auth available, send original data
                        log.warn("No preferred authentication available, sending original types for session: {}", sessionId);
                        
                        // Forward the original security types to the WebSocket client
                        vncConnection.webSocketConnection.sendBinary(buffer).subscribe().with(s -> {
                            vncConnection.updateStats(0, 0);
                        });
                    }
                    
                    log.info("Security types forwarded to client for session: {}", sessionId);
                    
                    // Stay in SECURITY state - wait for client to send their selection
                    
                } else {
                    log.warn("Incomplete security types data received: expected {} bytes, got {} bytes for session: {}", 
                            1 + numSecurityTypes, buffer.length(), sessionId);
                }
            } else {
                log.warn("Incomplete security data received: {} bytes for session: {}", buffer.length(), sessionId);
            }
        } catch (Exception e) {
            log.error("Error during security exchange for session: {}", sessionId, e);
            closeConnection(sessionId, WebSocketCloseStatus.INTERNAL_SERVER_ERROR, "Security exchange failed: " + e.getMessage());
        }
    }
    
    /**
     * Get a human-readable name for a security type
     */
    private String getSecurityTypeName(int securityType) {
        switch (securityType) {
            case 0: return "Invalid";
            case 1: return "None";
            case 2: return "VNC Authentication";
            case 5: return "RA2";
            case 6: return "RA2ne";
            case 16: return "Tight";
            case 17: return "Ultra";
            case 18: return "TLS";
            case 19: return "VeNCrypt";
            case 20: return "GTK-VNC SASL";
            case 21: return "MD5 hash authentication";
            case 22: return "Colin Dean xvp";
            default: return "Unknown(" + securityType + ")";
        }
    }
    
    /**
     * Handle authentication phase (placeholder for now)
     */
    private void handleAuth(String sessionId, Buffer buffer) {
        VNCConnection vncConnection = activeConnections.get(sessionId);
        if (vncConnection == null) {
            return;
        }

        // Forward auth data to WebSocket client
        vncConnection.webSocketConnection.sendBinary(buffer).subscribe().with(s -> { 
            vncConnection.updateStats(buffer.length(), 0);
        });
        
        // Move to connected state
        vncConnection.setState(VNCConnectionState.CONNECTED);
        vncConnection.isConnected = true;
        
        log.info("Authentication completed, connection ready for session: {}", sessionId);
    }

    /**
     * Handle VNC Authentication challenge-response
     */
    private void handleVNCAuth(String sessionId, Buffer buffer) {
        VNCConnection vncConnection = activeConnections.get(sessionId);
        if (vncConnection == null) {
            return;
        }

        try {
            // VNC server sends a 16-byte challenge
            if (buffer.length() == 16) {
                vncConnection.vncChallenge = buffer.getBytes();
                
                log.info("VNC authentication challenge received for session: {}", sessionId);
                
                // Automatically generate response using stored password
                byte[] response = encryptVNCChallenge(vncConnection.vncChallenge, vncServerPassword);
                if (response != null) {
                    vncConnection.vncResponse = response;
                    
                    log.info("VNC authentication response generated for session: {}", sessionId);
                    
                    // Send response to VNC server
                    Buffer responseBuffer = Buffer.buffer(response);
                    vncConnection.vncSocket.write(responseBuffer);
                    vncConnection.updateStats(0, responseBuffer.length());
                    
                    log.info("VNC authentication response sent to server for session: {}", sessionId);
                    
                    // Move to auth state for security result
                    vncConnection.setState(VNCConnectionState.AUTH);
                    vncConnection.vncAuthCompleted = true;
                } else {
                    log.error("Failed to generate VNC authentication response for session: {}", sessionId);
                    closeConnection(sessionId, WebSocketCloseStatus.INTERNAL_SERVER_ERROR, "Failed to generate VNC authentication response");
                }
                
            } else {
                log.warn("Invalid VNC challenge received: {} bytes for session: {}", buffer.length(), sessionId);
            }
        } catch (Exception e) {
            log.error("Error during VNC authentication for session: {}", sessionId, e);
            closeConnection(sessionId, WebSocketCloseStatus.INTERNAL_SERVER_ERROR, "VNC authentication failed: " + e.getMessage());
        }
    }

    @OnBinaryMessage
    public void onBinaryMessage(Buffer message, WebSocketConnection connection, @PathParam String sessionId) {
        VNCConnection vncConnection = activeConnections.get(sessionId);

        if (vncConnection != null && vncConnection.vncSocket != null) {
            try {
                // Handle different states
                switch (vncConnection.state) {
                    case PROTOCOL_VERSION:
                        // Client is sending their RFB version response
                        if (message.length() == 12) {
                            String clientVersion = message.toString();
                            vncConnection.clientRfbVersion = clientVersion;
                            
                            log.info("Client RFB version: {} for session: {}", clientVersion, sessionId);
                            
                            // Forward the client version to the VNC server
                            vncConnection.vncSocket.write(message);
                            vncConnection.updateStats(0, message.length());
                            
                            // Move to security state
                            vncConnection.setState(VNCConnectionState.SECURITY);
                            vncConnection.handshakeCompleted = true;
                            
                            log.info("Protocol version exchange completed for session: {}", sessionId);
                        } else {
                            log.warn("Invalid RFB version from client: {} bytes for session: {}", message.length(), sessionId);
                            logData("WebSocket->VNC", sessionId, message, true);
                        }
                        break;
                        
                    case SECURITY:
                        // Client is sending their security type selection
                        if (message.length() == 1) {
                            int clientSelectedType = message.getUnsignedByte(0);
                            
                            log.info("Client selected security type: {} ({}) for session: {}", 
                                    getSecurityTypeName(clientSelectedType), clientSelectedType, sessionId);
                            
                            // Use our stored selection for the server (might be different from client's choice)
                            int serverSelectedType = vncConnection.selectedSecurityType;
                            
                            log.info("Sending to server: {} ({}) for session: {}", 
                                    getSecurityTypeName(serverSelectedType), serverSelectedType, sessionId);
                            
                            // Create buffer with server's selection
                            Buffer serverSelection = Buffer.buffer(1);
                            serverSelection.setByte(0, (byte) serverSelectedType);
                            
                            // Forward the server selection to the VNC server
                            vncConnection.vncSocket.write(serverSelection);
                            vncConnection.updateStats(0, serverSelection.length());
                            
                            // Move to appropriate state based on server's security type
                            if (serverSelectedType == 1) { // None authentication
                                vncConnection.setState(VNCConnectionState.AUTH);
                                log.info("None authentication with server, skipping VNC auth phase for session: {}", sessionId);
                            } else if (serverSelectedType == 2) { // VNC Authentication
                                vncConnection.setState(VNCConnectionState.VNC_AUTH);
                                log.info("VNC Authentication with server, proceeding to challenge-response for session: {}", sessionId);
                            } else {
                                vncConnection.setState(VNCConnectionState.AUTH);
                                log.info("Other authentication type with server: {} for session: {}", serverSelectedType, sessionId);
                            }
                            
                            vncConnection.securityCompleted = true;
                            
                            log.info("Security type selection completed for session: {}", sessionId);
                        } else {
                            log.warn("Invalid security type selection from client: {} bytes for session: {}", message.length(), sessionId);
                        }
                        break;
                        
                    case CONNECTED:
                        // Normal data forwarding
                        vncConnection.vncSocket.write(message);
                        vncConnection.updateStats(0, message.length());
                        break;
                        
                    default:
                        log.warn("Received binary message in unexpected state: {} for session: {}", vncConnection.state, sessionId);
                        break;
                }
            } catch(Exception e) {
                log.error("Failed to process binary message", e);
                closeConnection(sessionId, WebSocketCloseStatus.INTERNAL_SERVER_ERROR, "Failed to process binary message: " + e.getMessage());
            }
        }
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
            // Set state to disconnected
            vncConnection.setState(VNCConnectionState.DISCONNECTED);
            
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
        logData(direction, sessionId, buffer, false);
    }
    
    private void logData(String direction, String sessionId, Buffer buffer, boolean logOverride) {

        if(!logOverride) {
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
     * Perform DES encryption for VNC authentication
     * @param challenge the 16-byte challenge from server
     * @param password the password to use as key
     * @return the 16-byte encrypted response
     */
    private byte[] encryptVNCChallenge(byte[] challenge, String password) {
        try {
            // VNC uses a specific key preparation method
            byte[] keyBytes = password.getBytes("ASCII");
            
            // Pad or truncate key to 8 bytes for DES
            byte[] desKey = new byte[8];
            System.arraycopy(keyBytes, 0, desKey, 0, Math.min(keyBytes.length, 8));
            
            // Reverse the bits in each byte (VNC specific)
            for (int i = 0; i < desKey.length; i++) {
                desKey[i] = reverseBits(desKey[i]);
            }
            
            // Create DES key and cipher
            DESKeySpec desKeySpec = new DESKeySpec(desKey);
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
            Cipher cipher = Cipher.getInstance("DES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keyFactory.generateSecret(desKeySpec));
            
            // Encrypt the challenge
            byte[] encrypted = cipher.doFinal(challenge);
            
            return encrypted;
            
        } catch (Exception e) {
            log.error("Error performing DES encryption", e);
            return null;
        }
    }
    
    /**
     * Reverse the bits in a byte (VNC specific)
     */
    private byte reverseBits(byte b) {
        byte result = 0;
        for (int i = 0; i < 8; i++) {
            result = (byte) ((result << 1) | (b & 1));
            b = (byte) (b >> 1);
        }
        return result;
    }
}
