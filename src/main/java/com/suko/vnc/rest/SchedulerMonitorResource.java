package com.suko.vnc.rest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.suko.vnc.security.VNCAuthService;

import io.quarkus.scheduler.Scheduler;
import io.quarkus.scheduler.Trigger;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/scheduler")
@Produces(MediaType.APPLICATION_JSON)
public class SchedulerMonitorResource {
    
    @Inject
    Scheduler scheduler;
    
    @Inject
    VNCAuthService authService;
    
    @GET
    @Path("/status")
    public Uni<Map<String, Object>> getSchedulerStatus() {
        List<Trigger> triggers = scheduler.getScheduledJobs();
        
        return Uni.createFrom().item(Map.of(
            "schedulerRunning", scheduler.isRunning(),
            "totalJobs", triggers.size(),
            "timestamp", LocalDateTime.now().toString(),
            "jobs", triggers.stream().map(this::mapTriggerInfo).collect(Collectors.toList())
        ));
    }
    
    @GET
    @Path("/jobs")
    public Uni<Map<String, Object>> getScheduledJobs() {
        List<Trigger> triggers = scheduler.getScheduledJobs();
        
        return Uni.createFrom().item(Map.of(
            "scheduledJobs", triggers.stream()
                .map(this::mapDetailedTriggerInfo)
                .collect(Collectors.toList()),
            "authServiceStats", Map.of(
                "activeSessions", authService.getActiveSessionCount(),
                "timestamp", LocalDateTime.now().toString()
            )
        ));
    }
    
    private Map<String, Object> mapTriggerInfo(Trigger trigger) {
        return Map.of(
            "id", trigger.getId(),
            "description", trigger.getMethodDescription(),
            "nextFireTime", trigger.getNextFireTime() != null ? trigger.getNextFireTime().toString() : "N/A",
            "previousFireTime", trigger.getPreviousFireTime() != null ? trigger.getPreviousFireTime().toString() : "N/A"
        );
    }
    
    private Map<String, Object> mapDetailedTriggerInfo(Trigger trigger) {
        return Map.of(
            "id", trigger.getId(),
            "description", trigger.getMethodDescription(),
            "nextFireTime", trigger.getNextFireTime() != null ? trigger.getNextFireTime().toString() : "N/A",
            "previousFireTime", trigger.getPreviousFireTime() != null ? trigger.getPreviousFireTime().toString() : "N/A",
            "running", scheduler.isRunning()
        );
    }
}
