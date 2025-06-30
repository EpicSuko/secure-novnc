package com.suko.vnc.rest;

import java.util.List;
import java.util.Map;

import com.suko.vnc.model.VNCTemplateData;
import com.suko.vnc.security.VNCAuthService;
import com.suko.vnc.websocket.VNCWebSocketProxy;

// import io.quarkus.qute.Location;
// import io.quarkus.qute.Template;
// import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/vnc")
@Produces(MediaType.TEXT_HTML)
public class VNCResource {
    
    @Inject
    VNCAuthService authService;
    
    @Inject
    VNCWebSocketProxy webSocketProxy;
    
    // @Inject
    // @Location("vncClient.qute.html")
    // Template vncClient;
    
    // @Inject
    // @Location("vncConnect.qute.html")
    // Template vncConnect;
    
    // @Inject
    // @Location("vncError.qute.html")
    // Template vncError;
    
    // @GET
    // public TemplateInstance vncClient() {
    //     return vncClient
    //         .data("appInfo", getAppInfo())
    //         .data("demoUsers", getDemoUsers())
    //         .data("vncServer", getVNCServerInfo())
    //         .data("proxyInfo", getProxyInfo());
    // }
    
    // @GET
    // @Path("/connect/{sessionId}")
    // public Response vncConnect(@PathParam("sessionId") String sessionId) {
    //     // Validate session first
    //     VNCAuthService.VNCSession session = authService.getSession(sessionId);
        
    //     if (session == null) {
    //         VNCTemplateData.ErrorInfo errorInfo = new VNCTemplateData.ErrorInfo(
    //             "Session Not Found",
    //             "Your session has expired or is invalid. Please authenticate again to access VNC.",
    //             "SESSION_EXPIRED",
    //             List.of(
    //                 "Try logging in again with your credentials",
    //                 "Check if your session expired (30 minute limit)",
    //                 "Verify the VNC server is running",
    //                 "Clear your browser cache and try again",
    //                 "Contact administrator if problem persists"
    //             ),
    //             "/vnc"
    //         );
            
    //         return Response.ok(
    //             vncError
    //                 .data("title", errorInfo.title)
    //                 .data("message", errorInfo.message)
    //                 .data("errorCode", errorInfo.errorCode)
    //                 .data("suggestions", errorInfo.suggestions)
    //                 .data("backUrl", errorInfo.backUrl)
    //                 .data("timestamp", errorInfo.timestamp)
    //         ).build();
    //     }
        
    //     VNCTemplateData.SessionInfo sessionInfo = new VNCTemplateData.SessionInfo(
    //         sessionId, session.userId, session.clientIP, 
    //         session.createdAt, session.lastActivity, session.isExpired()
    //     );
        
    //     return Response.ok(
    //         vncConnect
    //             .data("sessionInfo", sessionInfo)
    //             .data("vncServer", getVNCServerInfo())
    //             .data("proxyInfo", getProxyInfo())
    //     ).build();
    // }
    
    // @GET
    // @Path("/test-error")
    // public TemplateInstance testError() {
    //     VNCTemplateData.ErrorInfo errorInfo = new VNCTemplateData.ErrorInfo(
    //         "Test Error",
    //         "This is a test error page to demonstrate the error template functionality.",
    //         "TEST_ERROR",
    //         List.of(
    //             "This is just a test - no real error occurred",
    //             "You can use this to verify error template styling",
    //             "Try the navigation buttons below"
    //         ),
    //         "/vnc"
    //     );
        
    //     return vncError
    //         .data("title", errorInfo.title)
    //         .data("message", errorInfo.message)
    //         .data("errorCode", errorInfo.errorCode)
    //         .data("suggestions", errorInfo.suggestions)
    //         .data("backUrl", errorInfo.backUrl)
    //         .data("timestamp", errorInfo.timestamp);
    // }
    
    private List<VNCTemplateData.DemoUser> getDemoUsers() {
        return List.of(
            new VNCTemplateData.DemoUser("admin", "admin123", "Administrator", "admin"),
            new VNCTemplateData.DemoUser("user1", "user1pass", "Regular User", "user"),
            new VNCTemplateData.DemoUser("demo", "demo123", "Demo User", "demo")
        );
    }
    
    private VNCTemplateData.VNCServerInfo getVNCServerInfo() {
        boolean isRunning = checkVNCServerStatus();
        return new VNCTemplateData.VNCServerInfo(
            "localhost", "5901", "1280x1024", "vncpassword", isRunning
        );
    }
    
    private VNCTemplateData.AppInfo getAppInfo() {
        return new VNCTemplateData.AppInfo(
            "Secure VNC Application",
            "1.0.0-SNAPSHOT",
            "Phase 4: WebSockets Next Proxy",
            "Professional VNC client with reactive WebSocket proxy and real-time monitoring",
            List.of(
                "Secure Authentication",
                "Session Management", 
                "Qute Templates",
                "WebSockets Next Proxy",
                "Reactive Architecture",
                "Connection Monitoring",
                "Quarkus Scheduler",
                "Responsive Design"
            )
        );
    }
    
    private Map<String, Object> getProxyInfo() {
        return Map.of(
            "activeConnections", webSocketProxy.getActiveConnectionCount(),
            "type", "WebSockets Next Proxy",
            "technology", "Reactive Mutiny Streams",
            "features", List.of(
                "Session Validation",
                "Reactive Data Forwarding",
                "Non-blocking I/O",
                "Automatic Resource Management",
                "Connection Statistics",
                "Real-time Monitoring",
                "Enhanced Error Handling",
                "Optimized Performance"
            ),
            "benefits", List.of(
                "2x Faster WebSocket Handling",
                "50% Less Memory Usage", 
                "Better Error Recovery",
                "Improved Throughput",
                "Cloud-Native Ready"
            )
        );
    }
    
    private boolean checkVNCServerStatus() {
        try {
            java.net.Socket socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress("localhost", 5901), 1000);
            socket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}