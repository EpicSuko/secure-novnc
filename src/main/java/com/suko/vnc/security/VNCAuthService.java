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
        public String sessionId;
        public String userId;
        public String clientIP;
        public LocalDateTime createdAt;
        public LocalDateTime lastActivity;
        
        public VNCSession(String sessionId, String userId, String clientIP) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.clientIP = clientIP;
            this.createdAt = LocalDateTime.now();
            this.lastActivity = LocalDateTime.now();
        }
        
        public boolean isExpired() {
            return ChronoUnit.MINUTES.between(lastActivity, LocalDateTime.now()) > 30;
        }
        
        public void updateActivity() {
            this.lastActivity = LocalDateTime.now();
        }
    }

    @PostConstruct
    public void init() {
        System.out.println("🔐 Initializing VNC Authentication Service...");
        
        // Initialize demo users with hashed passwords
        initializeDemoUsers();
        
        System.out.println("✅ VNC Authentication Service initialized");
        System.out.println("👥 Demo users: admin, user1, demo");
        System.out.println("⏰ Scheduled cleanup will run every minute");
    }
    
    private void initializeDemoUsers() {
        // Demo users for Phase 2 testing
        preAuthUsers.put("admin", hashPassword("admin123"));
        preAuthUsers.put("user1", hashPassword("user1pass"));
        preAuthUsers.put("demo", hashPassword("demo123"));
        
        System.out.println("👥 Initialized demo users:");
        System.out.println("   - admin/admin123");
        System.out.println("   - user1/user1pass");
        System.out.println("   - demo/demo123");
    }

    public String authenticateAndCreateSession(String username, String preAuthPassword, String clientIP) {
        System.out.println("🔑 Authentication attempt: " + username + " from " + clientIP);
        
        // Check for rate limiting
        String attemptKey = clientIP + ":" + username;
        Integer attempts = failedAttempts.getOrDefault(attemptKey, 0);
        
        if (attempts >= 5) {
            System.out.println("🚫 Rate limited: " + attemptKey + " (" + attempts + " attempts)");
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
            
            System.out.println("✅ Authentication successful: " + username + " -> " + sessionId);
            return sessionId;
        } else {
            // Failed - increment attempts
            failedAttempts.put(attemptKey, attempts + 1);
            System.out.println("❌ Authentication failed: " + username + " (" + (attempts + 1) + " attempts)");
            return null;
        }
    }
    
    public VNCSession getSession(String sessionId) {
        VNCSession session = activeSessions.get(sessionId);
        if (session != null) {
            if (session.isExpired()) {
                System.out.println("⏰ Session expired: " + sessionId);
                activeSessions.remove(sessionId);
                return null;
            }
            session.updateActivity();
        }
        return session;
    }
    
    public void invalidateSession(String sessionId) {
        VNCSession session = activeSessions.remove(sessionId);
        if (session != null) {
            System.out.println("🚪 Session invalidated: " + sessionId + " (" + session.userId + ")");
        }
    }

    public int getActiveSessionCount() {
        return activeSessions.size();
    }
    
    public Map<String, VNCSession> getActiveSessions() {
        // Return copy for safety
        return Map.copyOf(activeSessions);
    }

    // Quarkus Scheduler - runs every minute to cleanup expired sessions
    @Scheduled(every = "1m", identity = "session-cleanup")
    void cleanupExpiredSessions() {
        int initialCount = activeSessions.size();
        
        activeSessions.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                System.out.println("🧹 Cleaning up expired session: " + entry.getKey() + " (" + entry.getValue().userId + ")");
                return true;
            }
            return false;
        });
        
        int removedCount = initialCount - activeSessions.size();
        if (removedCount > 0) {
            System.out.println("🧹 Session cleanup completed: removed " + removedCount + " expired sessions, " + activeSessions.size() + " active");
        }
    }
    
    // Quarkus Scheduler - runs every hour to cleanup failed attempts
    @Scheduled(cron = "0 0 * * * ?", identity = "failed-attempts-cleanup")
    void cleanupFailedAttempts() {
        int initialSize = failedAttempts.size();
        
        if (initialSize > 0) {
            failedAttempts.clear();
            System.out.println("🧹 Failed attempts cleanup: cleared " + initialSize + " entries");
        }
    }
    
    // Quarkus Scheduler - runs every 5 minutes for health monitoring
    @Scheduled(every = "5m", identity = "health-monitor", delay = 30)
    void healthMonitor() {
        int activeCount = activeSessions.size();
        int failedCount = failedAttempts.size();
        
        System.out.println("📊 Health Monitor - Active sessions: " + activeCount + ", Failed attempts tracked: " + failedCount);
        
        // Log warning if too many sessions
        if (activeCount > 50) {
            System.out.println("⚠️  High session count detected: " + activeCount + " active sessions");
        }
        
        // Log warning if too many failed attempts
        if (failedCount > 100) {
            System.out.println("⚠️  High failed attempt count: " + failedCount + " tracked IPs");
        }
    }
    
    private String generateSecureSessionId() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return bytesToHex(randomBytes);
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String saltedPassword = password + "VNCSecureSalt2024";
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
