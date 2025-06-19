package com.suko.vnc.model;

import java.time.LocalDateTime;
import java.util.List;

public class VNCTemplateData {
    
    public static class DemoUser {
        public final String username;
        public final String password;
        public final String description;
        public final String role;
        
        public DemoUser(String username, String password, String description, String role) {
            this.username = username;
            this.password = password;
            this.description = description;
            this.role = role;
        }
        
        public DemoUser(String username, String password, String description) {
            this(username, password, description, "user");
        }
    }
    
    public static class SessionInfo {
        public final String sessionId;
        public final String shortSessionId;
        public final String userId;
        public final String clientIP;
        public final String createdAt;
        public final String lastActivity;
        public final boolean isExpired;
        
        public SessionInfo(String sessionId, String userId, String clientIP, 
                          LocalDateTime createdAt, LocalDateTime lastActivity, boolean isExpired) {
            this.sessionId = sessionId;
            this.shortSessionId = sessionId.length() > 8 ? sessionId.substring(0, 8) + "..." : sessionId;
            this.userId = userId;
            this.clientIP = clientIP;
            this.createdAt = createdAt.toString();
            this.lastActivity = lastActivity.toString();
            this.isExpired = isExpired;
        }
    }
    
    public static class VNCServerInfo {
        public final String host;
        public final String port;
        public final String resolution;
        public final String password;
        public final boolean isRunning;
        public final String webUrl;
        
        public VNCServerInfo(String host, String port, String resolution, String password, boolean isRunning) {
            this.host = host;
            this.port = port;
            this.resolution = resolution;
            this.password = password;
            this.isRunning = isRunning;
            this.webUrl = "http://" + host + ":6901/?password=" + password;
        }
    }
    
    public static class AppInfo {
        public final String name;
        public final String version;
        public final String phase;
        public final String description;
        public final List<String> features;
        public final String buildTime;
        
        public AppInfo(String name, String version, String phase, String description, List<String> features) {
            this.name = name;
            this.version = version;
            this.phase = phase;
            this.description = description;
            this.features = features;
            this.buildTime = java.time.LocalDateTime.now().toString();
        }
    }
    
    public static class ErrorInfo {
        public final String title;
        public final String message;
        public final String errorCode;
        public final List<String> suggestions;
        public final String backUrl;
        public final String timestamp;
        
        public ErrorInfo(String title, String message, String errorCode, List<String> suggestions, String backUrl) {
            this.title = title;
            this.message = message;
            this.errorCode = errorCode;
            this.suggestions = suggestions;
            this.backUrl = backUrl;
            this.timestamp = java.time.LocalDateTime.now().toString();
        }
    }
}
