package com.suko.vnc.rest;

import java.time.LocalDateTime;
import java.util.Map;

import com.suko.vnc.security.VNCAuthService;
import com.suko.vnc.websocket.VNCWebSocketProxy;

import io.quarkus.scheduler.Scheduler;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    @Inject
    VNCAuthService authService;
    
    @Inject
    Scheduler scheduler;

    @Inject
    VNCWebSocketProxy webSocketProxy;
    
    @GET
    public Uni<Map<String, Object>> health() {
        return Uni.createFrom().item(Map.of(
            "status", "UP",
            "phase", "Phase 4 - WebSocket Proxy",
            "timestamp", LocalDateTime.now().toString(),
            "message", "Secure VNC Application with WebSocket Proxy!",
            "activeSessions", authService.getActiveSessionCount(),
            "activeConnections", webSocketProxy.getActiveConnectionCount(),
            "scheduler", Map.of(
                "running", scheduler.isRunning(),
                "jobCount", scheduler.getScheduledJobs().size()
            ),
            "endpoints", Map.of(
                "health", "/api/health",
                "authenticate", "/api/vnc/authenticate",
                "sessions", "/api/vnc/sessions",
                "proxy", "/api/proxy/status",
                "websocket", "/websockify/{sessionId}",
                "scheduler", "/api/scheduler/status",
                "home", "/",
                "dev-ui", "/q/dev/"
            )
        ));
    }
    
    @GET
    @Path("/detailed")
    public Uni<Map<String, Object>> detailedHealth() {
        return Uni.createFrom().item(Map.of(
            "application", Map.of(
                "name", "secure-vnc-app",
                "version", "1.0.0-SNAPSHOT", 
                "phase", "4",
                "description", "WebSocket Proxy for VNC connections"
            ),
            "authentication", Map.of(
                "activeSessions", authService.getActiveSessionCount(),
                "demoUsers", "admin, user1, demo",
                "sessionTimeout", "30 minutes"
            ),
            "proxy", Map.of(
                "activeConnections", webSocketProxy.getActiveConnectionCount(),
                "type", "VNC WebSocket Proxy",
                "features", new String[]{
                    "Session Validation",
                    "Bidirectional Forwarding",
                    "Connection Statistics", 
                    "Auto Cleanup"
                }
            ),
            "scheduler", Map.of(
                "running", scheduler.isRunning(),
                "scheduledJobs", scheduler.getScheduledJobs().stream()
                    .map(trigger -> Map.of(
                        "id", trigger.getId(),
                        "description", trigger.getMethodDescription(),
                        "nextRun", trigger.getNextFireTime() != null ? trigger.getNextFireTime().toString() : "N/A"
                    ))
                    .toList()
            ),
            "system", Map.of(
                "java-version", System.getProperty("java.version"),
                "os", System.getProperty("os.name"),
                "memory", Map.of(
                    "total", Runtime.getRuntime().totalMemory(),
                    "free", Runtime.getRuntime().freeMemory(),
                    "max", Runtime.getRuntime().maxMemory()
                )
            ),
            "status", "UP",
            "timestamp", LocalDateTime.now().toString()
        ));
    }

}
