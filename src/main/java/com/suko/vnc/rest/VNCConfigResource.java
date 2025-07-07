package com.suko.vnc.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Context;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.suko.vnc.security.VNCAuthService;

import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;

@Path("/api/vnc")
@Produces(MediaType.APPLICATION_JSON)
public class VNCConfigResource {
    
    @Inject
    VNCAuthService authService;
    
    @Context
    RoutingContext context;
    
    @ConfigProperty(name = "vnc.websocket.host", defaultValue = "localhost")
    String wsHost;
    
    @ConfigProperty(name = "vnc.websocket.port", defaultValue = "8080")
    int wsPort;
    
    @ConfigProperty(name = "vnc.websocket.protocol", defaultValue = "ws")
    String wsProtocol;
    
    public static class ConfigResponse {
        public String wsHost;
        public int wsPort;
        public String wsUrl;
        public String protocol;
        public boolean success;
        public String message;
        public long timestamp;
        
        public ConfigResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
        
        public ConfigResponse(String wsHost, int wsPort, String sessionId) {
            this.wsHost = wsHost;
            this.wsPort = wsPort;
            this.wsUrl = "/websockify/" + sessionId;
            this.protocol = "ws";
            this.success = true;
            this.message = "Configuration retrieved successfully";
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    @GET
    @Path("/config/{sessionId}")
    public Uni<Response> getConfig(@PathParam("sessionId") String sessionId) {
        System.out.println("⚙️ Config request received for session: " + sessionId);
        
        // Validate session
        VNCAuthService.VNCSession session = authService.getSession(sessionId);
        
        if (session == null) {
            System.out.println("❌ Invalid or expired session for config request: " + sessionId);
            return Uni.createFrom().item(Response.status(401)
                .entity(new ConfigResponse(false, "Invalid or expired session"))
                .build());
        }
        
        // Get client IP for logging
        String clientIP = getClientIP();
        System.out.println("✅ Config request authorized for session: " + sessionId + " (" + session.getUserId() + ") from " + clientIP);
        
        // Use configured WebSocket settings
        String finalWsHost = wsHost;
        int finalWsPort = wsPort;
        String finalProtocol = wsProtocol;
        
        // If running on localhost, use the client's IP for WebSocket connection
        if ("localhost".equals(finalWsHost) || "127.0.0.1".equals(finalWsHost)) {
            // For localhost, we'll use the same host but the client should connect to the server's actual IP
            // In production, this would be the server's public IP
            finalWsHost = "localhost";
        }
        
        // Use secure WebSocket if not on localhost
        if ("localhost".equals(finalWsHost) || "127.0.0.1".equals(finalWsHost)) {
            finalProtocol = "ws";
        } else {
            finalProtocol = "wss";
        }
        
        ConfigResponse config = new ConfigResponse(finalWsHost, finalWsPort, sessionId);
        config.protocol = finalProtocol;
        
        return Uni.createFrom().item(Response.ok(config).build());
    }
    
    private String getClientIP() {
        String xForwardedFor = context.request().getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIP = context.request().getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }
        
        return context.request().remoteAddress().toString();
    }
} 