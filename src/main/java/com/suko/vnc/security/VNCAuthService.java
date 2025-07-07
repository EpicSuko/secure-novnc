package com.suko.vnc.security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VNCAuthService {

    private final Map<String, VNCSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, String> preAuthUsers = new ConcurrentHashMap<>();
    private final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    public static class VNCSession {
        private final String sessionId;
        private final String userId;
        private final String clientIP;
        private final LocalDateTime createdAt;
        private LocalDateTime lastActivity;
        
        public VNCSession(String sessionId, String userId, String clientIP) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.clientIP = clientIP;
            this.createdAt = LocalDateTime.now();
            this.lastActivity = LocalDateTime.now();
        }
        
        public String getSessionId() { return sessionId; }
        public String getUserId() { return userId; }
        public String getClientIP() { return clientIP; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getLastActivity() { return lastActivity; }
        
        public boolean isExpired() {
            return ChronoUnit.MINUTES.between(lastActivity, LocalDateTime.now()) > 30;
        }
        
        public void updateActivity() {
            this.lastActivity = LocalDateTime.now();
        }
    }

    @PostConstruct
    public void init() {
        // Initialize demo users
        preAuthUsers.put("admin", hashPassword("admin123"));
        preAuthUsers.put("user1", hashPassword("user1pass"));
        preAuthUsers.put("demo", hashPassword("demo123"));
    }

    public String authenticateAndCreateSession(String username, String preAuthPassword, String clientIP) {
        // Check for rate limiting
        String attemptKey = clientIP + ":" + username;
        Integer attempts = failedAttempts.getOrDefault(attemptKey, 0);
        
        if (attempts >= 5) {
            return null;
        }
        
        // Validate credentials
        String hashedInput = hashPassword(preAuthPassword);
        String storedHash = preAuthUsers.get(username);
        
        if (storedHash != null && storedHash.equals(hashedInput)) {
            // Success - reset failed attempts
            failedAttempts.remove(attemptKey);
            
            // Create session
            String sessionId = generateSecureSessionId();
            VNCSession session = new VNCSession(sessionId, username, clientIP);
            activeSessions.put(sessionId, session);
            
            return sessionId;
        } else {
            // Failed - increment attempts
            failedAttempts.put(attemptKey, attempts + 1);
            return null;
        }
    }
    
    public VNCSession getSession(String sessionId) {
        VNCSession session = activeSessions.get(sessionId);
        if (session != null) {
            if (session.isExpired()) {
                activeSessions.remove(sessionId);
                return null;
            }
            session.updateActivity();
        }
        return session;
    }
    
    public void invalidateSession(String sessionId) {
        activeSessions.remove(sessionId);
    }
    
    public Map<String, VNCSession> getActiveSessions() {
        return Map.copyOf(activeSessions);
    }

    @Scheduled(every = "1m", identity = "session-cleanup")
    void cleanupExpiredSessions() {
        activeSessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    @Scheduled(cron = "0 0 * * * ?", identity = "failed-attempts-cleanup")
    void cleanupFailedAttempts() {
        failedAttempts.clear();
    }
    
    private String generateSecureSessionId() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return bytesToHex(randomBytes);
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String saltedPassword = password + "VNCSecureSalt2025";
            byte[] hash = md.digest(saltedPassword.getBytes());
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
