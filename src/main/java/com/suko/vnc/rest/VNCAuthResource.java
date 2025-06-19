package com.suko.vnc.rest;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

import com.suko.vnc.security.VNCAuthService;

import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;

@Path("/api/vnc")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VNCAuthResource {
    
    @Inject
    VNCAuthService authService;

    @Context
    RoutingContext context;
    
    public static class AuthRequest {
        public String username;
        public String preAuthPassword;
        
        // Default constructor for JSON deserialization
        public AuthRequest() {}
        
        public AuthRequest(String username, String preAuthPassword) {
            this.username = username;
            this.preAuthPassword = preAuthPassword;
        }
    }
    
    public static class AuthResponse {
        public String sessionId;
        public String wsUrl;
        public boolean success;
        public String message;
        public String userId;
        public long timestamp;
        
        public AuthResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
        
        public AuthResponse(String sessionId, String userId) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.wsUrl = "/websockify/" + sessionId;
            this.success = true;
            this.message = "Authentication successful";
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    public static class SessionResponse {
        public boolean valid;
        public String message;
        public String wsUrl;
        public String userId;
        public long lastActivity;
        public long timestamp;
        
        public SessionResponse(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
        
        public SessionResponse(VNCAuthService.VNCSession session) {
            this.valid = true;
            this.message = "Session is valid";
            this.wsUrl = "/websockify/" + session.sessionId;
            this.userId = session.userId;
            this.lastActivity = session.lastActivity.toEpochSecond(java.time.ZoneOffset.UTC) * 1000;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    @POST
    @Path("/authenticate")
    public Uni<Response> authenticate(AuthRequest authRequest) {
        System.out.println("🔐 Authentication request received");
        
        // Validate input
        if (authRequest == null || authRequest.username == null || authRequest.preAuthPassword == null) {
            System.out.println("❌ Invalid request: missing username or password");
            return Uni.createFrom().item(Response.status(400)
                .entity(new AuthResponse(false, "Username and password are required"))
                .build());
        }
        
        if (authRequest.username.trim().isEmpty() || authRequest.preAuthPassword.trim().isEmpty()) {
            System.out.println("❌ Invalid request: empty username or password");
            return Uni.createFrom().item(Response.status(400)
                .entity(new AuthResponse(false, "Username and password cannot be empty"))
                .build());
        }
        
        // Get client IP
        String clientIP = getClientIP();
        
        // Attempt authentication
        String sessionId = authService.authenticateAndCreateSession(
            authRequest.username.trim(), 
            authRequest.preAuthPassword, 
            clientIP
        );
        
        if (sessionId != null) {
            System.out.println("✅ Authentication successful for: " + authRequest.username);
            return Uni.createFrom().item(Response.ok(new AuthResponse(sessionId, authRequest.username)).build());
        } else {
            System.out.println("❌ Authentication failed for: " + authRequest.username);
            return Uni.createFrom().item(Response.status(401)
                .entity(new AuthResponse(false, "Invalid username or password"))
                .build());
        }
    }
    
    @GET
    @Path("/session/{sessionId}/validate")
    public Uni<Response> validateSession(@PathParam("sessionId") String sessionId) {
        System.out.println("🔍 Session validation request: " + sessionId);
        
        VNCAuthService.VNCSession session = authService.getSession(sessionId);
        
        if (session != null) {
            System.out.println("✅ Session valid: " + sessionId + " (" + session.userId + ")");
            return Uni.createFrom().item(Response.ok(new SessionResponse(session)).build());
        } else {
            System.out.println("❌ Session invalid or expired: " + sessionId);
            return Uni.createFrom().item(Response.status(404)
                .entity(new SessionResponse(false, "Session not found or expired"))
                .build());
        }
    }
    
    @DELETE
    @Path("/session/{sessionId}")
    public Uni<Response> logout(@PathParam("sessionId") String sessionId) {
        System.out.println("🚪 Logout request: " + sessionId);
        
        authService.invalidateSession(sessionId);
        return Uni.createFrom().item(Response.ok(new SessionResponse(true, "Session invalidated successfully")).build());
    }
    
    @GET
    @Path("/sessions")
    public Uni<Response> getActiveSessions() {
        Map<String, VNCAuthService.VNCSession> sessions = authService.getActiveSessions();
        
        return Uni.createFrom().item(Response.ok(Map.of(
            "activeSessionCount", sessions.size(),
            "sessions", sessions.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> Map.of(
                        "userId", entry.getValue().userId,
                        "clientIP", entry.getValue().clientIP,
                        "createdAt", entry.getValue().createdAt.toString(),
                        "lastActivity", entry.getValue().lastActivity.toString()
                    )
                ))
        )).build());
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
