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
import java.util.stream.Collectors;

import com.suko.vnc.security.VNCAuthService;

import io.quarkus.runtime.annotations.RegisterForReflection;
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
    
    @RegisterForReflection
    public static class AuthRequest {
        private String username;
        private String preAuthPassword;
        
        // Default constructor for JSON deserialization
        public AuthRequest() {}
        
        public AuthRequest(String username, String preAuthPassword) {
            this.username = username;
            this.preAuthPassword = preAuthPassword;
        }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPreAuthPassword() { return preAuthPassword; }
        public void setPreAuthPassword(String preAuthPassword) { this.preAuthPassword = preAuthPassword; }
        
        public boolean isValid() {
            return !getTrimmedUsername().isEmpty() && 
                   preAuthPassword != null && !preAuthPassword.trim().isEmpty();
        }
        
        public String getTrimmedUsername() {
            return username != null ? username.trim() : "";
        }
    }
    
    @RegisterForReflection
    public static class AuthResponse {
        private final String sessionId;
        private final String wsUrl;
        private final boolean success;
        private final String message;
        private final String userId;
        private final long timestamp;
        
        public AuthResponse(boolean success, String message) {
            this.sessionId = null;
            this.wsUrl = null;
            this.success = success;
            this.message = message;
            this.userId = null;
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
        
        // Getters
        public String getSessionId() { return sessionId; }
        public String getWsUrl() { return wsUrl; }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getUserId() { return userId; }
        public long getTimestamp() { return timestamp; }
    }
    
    @RegisterForReflection
    public static class SessionResponse {
        private final boolean valid;
        private final String message;
        private final String wsUrl;
        private final String userId;
        private final long lastActivity;
        private final long timestamp;
        
        public SessionResponse(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
            this.wsUrl = null;
            this.userId = null;
            this.lastActivity = 0;
            this.timestamp = System.currentTimeMillis();
        }
        
        public SessionResponse(VNCAuthService.VNCSession session) {
            this.valid = true;
            this.message = "Session is valid";
            this.wsUrl = "/websockify/" + session.getSessionId();
            this.userId = session.getUserId();
            this.lastActivity = session.getLastActivity().toEpochSecond(java.time.ZoneOffset.UTC) * 1000;
            this.timestamp = System.currentTimeMillis();
        }
        
        // Getters
        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
        public String getWsUrl() { return wsUrl; }
        public String getUserId() { return userId; }
        public long getLastActivity() { return lastActivity; }
        public long getTimestamp() { return timestamp; }
    }
    
    @POST
    @Path("/authenticate")
    public Uni<Response> authenticate(AuthRequest authRequest) {
        // Validate input
        if (authRequest == null || !authRequest.isValid()) {
            return createErrorResponse(400, "Username and password are required");
        }
        
        // Attempt authentication
        String sessionId = authService.authenticateAndCreateSession(
            authRequest.getTrimmedUsername(), 
            authRequest.getPreAuthPassword(), 
            getClientIP()
        );
        
        if (sessionId != null) {
            return Uni.createFrom().item(Response.ok(new AuthResponse(sessionId, authRequest.getTrimmedUsername())).build());
        } else {
            return createErrorResponse(401, "Invalid username or password");
        }
    }
    
    @GET
    @Path("/session/{sessionId}/validate")
    public Uni<Response> validateSession(@PathParam("sessionId") String sessionId) {
        VNCAuthService.VNCSession session = authService.getSession(sessionId);
        
        if (session != null) {
            return Uni.createFrom().item(Response.ok(new SessionResponse(session)).build());
        } else {
            return createErrorResponse(404, "Session not found or expired");
        }
    }
    
    @DELETE
    @Path("/session/{sessionId}")
    public Uni<Response> logout(@PathParam("sessionId") String sessionId) {
        authService.invalidateSession(sessionId);
        return Uni.createFrom().item(Response.ok(new SessionResponse(true, "Session invalidated successfully")).build());
    }
    
    @GET
    @Path("/sessions")
    public Uni<Response> getActiveSessions() {
        Map<String, VNCAuthService.VNCSession> sessions = authService.getActiveSessions();
        
        Map<String, Object> response = Map.of(
            "activeSessionCount", sessions.size(),
            "sessions", sessions.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> Map.of(
                        "userId", entry.getValue().getUserId(),
                        "clientIP", entry.getValue().getClientIP(),
                        "createdAt", entry.getValue().getCreatedAt().toString(),
                        "lastActivity", entry.getValue().getLastActivity().toString()
                    )
                ))
        );
        
        return Uni.createFrom().item(Response.ok(response).build());
    }
    
    private Uni<Response> createErrorResponse(int status, String message) {
        return Uni.createFrom().item(Response.status(status)
            .entity(new AuthResponse(false, message))
            .build());
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
