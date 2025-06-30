package com.suko.vnc.websocket;

import javax.crypto.Cipher;
import javax.crypto.spec.DESKeySpec;
import javax.crypto.SecretKeyFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.buffer.Buffer;

/**
 * Handles RFB protocol logic for VNC proxy
 */
public class VNCProtocolHandler {
    
    private static final Logger log = LoggerFactory.getLogger(VNCProtocolHandler.class);
    
    private final String sessionId;
    private final VNCConnection connection;
    private final VNCClientHandler clientHandler;
    private final VNCServerHandler serverHandler;
    
    public VNCProtocolHandler(String sessionId, VNCConnection connection, 
                             VNCClientHandler clientHandler, VNCServerHandler serverHandler) {
        this.sessionId = sessionId;
        this.connection = connection;
        this.clientHandler = clientHandler;
        this.serverHandler = serverHandler;
    }
    
    /**
     * Handle data received from the VNC server
     */
    public void handleServerData(Buffer buffer) {
        // Update stats for data received from VNC server
        clientHandler.handleReceivedData(buffer);
        
        switch (connection.state) {
            case PROTOCOL_VERSION:
                handleProtocolVersion(buffer);
                break;
            case SECURITY:
                handleSecurity(buffer);
                break;
            case VNC_AUTH:
                handleVNCAuth(buffer);
                break;
            case AUTH:
                handleAuth(buffer);
                break;
            case CONNECTED:
                // Forward data to WebSocket client
                clientHandler.sendBinary(buffer);
                break;
            default:
                log.warn("Received data in unexpected state: {} for session: {}", connection.state, sessionId);
                break;
        }
    }
    
    /**
     * Handle data received from the WebSocket client
     */
    public void handleClientData(Buffer buffer) {
        // Update stats for data received from WebSocket client
        serverHandler.handleReceivedData(buffer);
        
        switch (connection.state) {
            case PROTOCOL_VERSION:
                handleClientProtocolVersion(buffer);
                break;
            case SECURITY:
                handleClientSecurity(buffer);
                break;
            case CONNECTED:
                // Normal data forwarding
                serverHandler.sendData(buffer);
                break;
            default:
                log.warn("Received client data in unexpected state: {} for session: {}", connection.state, sessionId);
                break;
        }
    }
    
    /**
     * Handle the RFB protocol version exchange with the VNC server
     */
    private void handleProtocolVersion(Buffer buffer) {
        try {
            // RFB version string is 12 bytes
            if (buffer.length() >= 12) {
                String serverVersion = buffer.getString(0, 12);
                connection.serverRfbVersion = serverVersion;
                
                log.info("VNC server version: {} for session: {}", serverVersion, sessionId);
                
                // Forward the server version to the WebSocket client
                clientHandler.sendBinary(buffer);
                
                log.info("Protocol version forwarded to client for session: {}", sessionId);
                
                // Stay in PROTOCOL_VERSION state - wait for client to send their version
                
            } else {
                log.warn("Incomplete protocol version data received: {} bytes for session: {}", buffer.length(), sessionId);
            }
        } catch (Exception e) {
            log.error("Error during protocol version exchange for session: {}", sessionId, e);
            throw new RuntimeException("Protocol version exchange failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Handle client's RFB protocol version response
     */
    private void handleClientProtocolVersion(Buffer buffer) {
        if (buffer.length() == 12) {
            String clientVersion = buffer.toString();
            connection.clientRfbVersion = clientVersion;
            
            log.info("Client RFB version: {} for session: {}", clientVersion, sessionId);
            
            // Check if VNC socket is ready before sending data
            if (connection.vncSocket != null) {
                // Forward the client version to the VNC server
                serverHandler.sendData(buffer);
                
                // Move to security state
                connection.setState(VNCConnectionState.SECURITY);
                connection.handshakeCompleted = true;
                
                log.info("Protocol version exchange completed for session: {}", sessionId);
            } else {
                // VNC socket not ready yet, store the client version and wait
                log.info("VNC socket not ready yet, storing client version for session: {}", sessionId);
                connection.pendingClientProtocolVersion = buffer;
                // We'll send this when the socket becomes available
            }
        } else {
            log.warn("Invalid RFB version from client: {} bytes for session: {}", buffer.length(), sessionId);
        }
    }
    
    /**
     * Called when VNC socket becomes available - send any pending data
     */
    public void onVNCSocketReady() {
        if (connection.pendingClientProtocolVersion != null && connection.state == VNCConnectionState.PROTOCOL_VERSION) {
            log.info("VNC socket ready, sending pending client protocol version for session: {}", sessionId);
            
            // Send the pending client protocol version
            serverHandler.sendData(connection.pendingClientProtocolVersion);
            
            // Move to security state
            connection.setState(VNCConnectionState.SECURITY);
            connection.handshakeCompleted = true;
            
            // Clear pending data
            connection.pendingClientProtocolVersion = null;
            
            log.info("Pending protocol version exchange completed for session: {}", sessionId);
        }
    }
    
    /**
     * Handle the security types exchange with the VNC server
     */
    private void handleSecurity(Buffer buffer) {
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
                    connection.serverSecurityTypes = securityTypes;
                    
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
                        clientHandler.sendBinary(noneAuthToClient);
                        
                        // Store that we need to handle VNC auth with server
                        connection.selectedSecurityType = 2; // VNC Authentication for server
                    } else if (noneAuthAvailable) {
                        // Only None auth available, send it to both client and server
                        Buffer noneAuthOnly = Buffer.buffer(2);
                        noneAuthOnly.setByte(0, (byte) 1); // 1 security type
                        noneAuthOnly.setByte(1, (byte) 1); // None authentication
                        
                        log.info("Only None auth available, sending None authentication for session: {}", sessionId);
                        
                        // Forward only None authentication to the WebSocket client
                        clientHandler.sendBinary(noneAuthOnly);
                        
                        connection.selectedSecurityType = 1; // None authentication
                    } else {
                        // If no preferred auth available, send original data
                        log.warn("No preferred authentication available, sending original types for session: {}", sessionId);
                        
                        // Forward the original security types to the WebSocket client
                        clientHandler.sendBinary(buffer);
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
            throw new RuntimeException("Security exchange failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Handle client's security type selection
     */
    private void handleClientSecurity(Buffer buffer) {
        if (buffer.length() == 1) {
            int clientSelectedType = buffer.getUnsignedByte(0);
            
            log.info("Client selected security type: {} ({}) for session: {}", 
                    getSecurityTypeName(clientSelectedType), clientSelectedType, sessionId);
            
            // Use our stored selection for the server (might be different from client's choice)
            int serverSelectedType = connection.selectedSecurityType;
            
            log.info("Sending to server: {} ({}) for session: {}", 
                    getSecurityTypeName(serverSelectedType), serverSelectedType, sessionId);
            
            // Create buffer with server's selection
            Buffer serverSelection = Buffer.buffer(1);
            serverSelection.setByte(0, (byte) serverSelectedType);
            
            // Forward the server selection to the VNC server
            serverHandler.sendData(serverSelection);
            
            // Move to appropriate state based on server's security type
            if (serverSelectedType == 1) { // None authentication
                connection.setState(VNCConnectionState.AUTH);
                log.info("None authentication with server, skipping VNC auth phase for session: {}", sessionId);
            } else if (serverSelectedType == 2) { // VNC Authentication
                connection.setState(VNCConnectionState.VNC_AUTH);
                log.info("VNC Authentication with server, proceeding to challenge-response for session: {}", sessionId);
            } else {
                connection.setState(VNCConnectionState.AUTH);
                log.info("Other authentication type with server: {} for session: {}", serverSelectedType, sessionId);
            }
            
            connection.securityCompleted = true;
            
            log.info("Security type selection completed for session: {}", sessionId);
        } else {
            log.warn("Invalid security type selection from client: {} bytes for session: {}", buffer.length(), sessionId);
        }
    }
    
    /**
     * Handle authentication phase
     */
    private void handleAuth(Buffer buffer) {
        // Forward auth data to WebSocket client
        clientHandler.sendBinary(buffer);
        
        // Move to connected state
        connection.setState(VNCConnectionState.CONNECTED);
        connection.isConnected = true;
        
        log.info("Authentication completed, connection ready for session: {}", sessionId);
    }
    
    /**
     * Handle VNC Authentication challenge-response
     */
    private void handleVNCAuth(Buffer buffer) {
        try {
            // VNC server sends a 16-byte challenge
            if (buffer.length() == 16) {
                connection.vncChallenge = buffer.getBytes();
                
                log.info("VNC authentication challenge received for session: {}", sessionId);
                
                // Automatically generate response using stored password
                byte[] response = encryptVNCChallenge(connection.vncChallenge, serverHandler.getVncServerPassword());
                if (response != null) {
                    connection.vncResponse = response;
                    
                    log.info("VNC authentication response generated for session: {}", sessionId);
                    
                    // Send response to VNC server
                    Buffer responseBuffer = Buffer.buffer(response);
                    serverHandler.sendData(responseBuffer);
                    
                    log.info("VNC authentication response sent to server for session: {}", sessionId);
                    
                    // Move to auth state for security result
                    connection.setState(VNCConnectionState.AUTH);
                    connection.vncAuthCompleted = true;
                } else {
                    log.error("Failed to generate VNC authentication response for session: {}", sessionId);
                    throw new RuntimeException("Failed to generate VNC authentication response");
                }
                
            } else {
                log.warn("Invalid VNC challenge received: {} bytes for session: {}", buffer.length(), sessionId);
            }
        } catch (Exception e) {
            log.error("Error during VNC authentication for session: {}", sessionId, e);
            throw new RuntimeException("VNC authentication failed: " + e.getMessage(), e);
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