package com.suko.vnc.websocket;

/**
 * Represents the different states of a VNC connection
 */
public enum VNCConnectionState {
    CONNECTING("Connecting to VNC server"),
    PROTOCOL_VERSION("Exchanging RFB protocol version"),
    SECURITY("Exchanging security types"),
    VNC_AUTH("VNC Authentication challenge-response"),
    AUTH("Authenticating with VNC server"),
    CONNECTED("Connected and ready"),
    DISCONNECTED("Disconnected");
    
    private final String description;
    
    VNCConnectionState(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
} 